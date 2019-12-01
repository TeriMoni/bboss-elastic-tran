package org.frameworkset.tran.db.output;

import com.frameworkset.util.VariableHandler;
import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.tran.FieldMeta;
import org.frameworkset.tran.TranErrorWrapper;
import org.frameworkset.tran.context.Context;
import org.frameworkset.tran.context.ContextImpl;
import org.frameworkset.tran.context.ImportContext;
import org.frameworkset.tran.metrics.ImportCount;
import org.frameworkset.tran.metrics.ParallImportCount;
import org.frameworkset.tran.metrics.SerialImportCount;
import org.frameworkset.tran.schedule.Status;
import org.frameworkset.tran.task.TaskCall;
import org.frameworkset.tran.task.TaskCommand;
import org.frameworkset.tran.BaseDataTran;
import org.frameworkset.tran.Param;
import org.frameworkset.tran.TranResultSet;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class DBOutPutDataTran<T> extends BaseDataTran {
	protected DBOutPutContext es2DBContext ;
	@Override
	public void logTaskStart(Logger logger) {
		logger.info(new StringBuilder().append("import data to db[").append(importContext.getDbConfig().getDbUrl())
				.append("] dbuser[").append(importContext.getDbConfig().getDbUser()).append(" sql[").append(es2DBContext.getSqlInfo().getOriginSQL()).append("] start.").toString());
	}
	protected void init(){
		es2DBContext = (DBOutPutContext)importContext;

	}


	public DBOutPutDataTran(TranResultSet jdbcResultSet, ImportContext importContext) {
		super(jdbcResultSet,importContext);
	}


	public String serialExecute(  ){
		Object lastValue = null;
		Exception exception = null;
		long start = System.currentTimeMillis();
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		ImportCount importCount = new SerialImportCount();
		long totalCount = 0;
		long ignoreTotalCount = 0;

		try {

			//		GetCUDResult CUDResult = null;
			TranSQLInfo sqlinfo = es2DBContext.getSqlInfo();
			Object temp = null;
			Param param = null;
			List<List<Param>> records = new ArrayList<List<Param>>();
			while (jdbcResultSet.next()) {
				try {
					if (lastValue == null)
						lastValue = importContext.max(currentValue, getLastValue());
					else {
						lastValue = importContext.max(lastValue, getLastValue());
					}
					Context context = new ContextImpl(importContext, jdbcResultSet, null);
					context.refactorData();
					if (context.isDrop()) {
						importCount.increamentIgnoreTotalCount();
						continue;
					}
					List<Param> record = buildRecord(  context,  sqlinfo.getVars() );

					records.add(record);
					//						evalBuilk(this.jdbcResultSet, batchContext, writer, context, "index", clientInterface.isVersionUpper7());
					totalCount++;
				} catch (Exception e) {
					throw new ElasticSearchException(e);
				}
			}
			TaskCommand<List<List<Param>>, String> taskCommand = new Base2DBTaskCommandImpl(sqlinfo.getSql(),importCount,importContext,records,1,importCount.getJobNo());
			TaskCall.call(taskCommand);
			importContext.flushLastValue(lastValue);
			if(isPrintTaskLog()) {
				long end = System.currentTimeMillis();
				logger.info(new StringBuilder().append("All Take time:").append((end - start)).append("ms")
						.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
						.append(importCount.getIgnoreTotalCount()).append(" records.").toString());

			}
		}
		catch (ElasticSearchException e){
			exception = e;
			throw e;


		}
		catch (Exception e){
			exception = e;
			throw new ElasticSearchException(e);


		} finally {

			if(!TranErrorWrapper.assertCondition(exception ,importContext)){
				stop();
			}
			if(importContext.isCurrentStoped()){
				stop();
			}
			importCount.setJobEndTime(new Date());
		}
		return null;

	}
	private List<Param> buildRecord(Context context, List<VariableHandler.Variable> vars ){
		Object temp = null;
		Param param = null;
		List<Param> record = new ArrayList<Param>();
		Map<String,Object> addedFields = new HashMap<String,Object>();

		List<FieldMeta> fieldValueMetas = context.getFieldValues();//context优先级高于，全局配置，全局配置高于字段值

		appendFieldValues( record, vars,    fieldValueMetas,  addedFields);
		fieldValueMetas = context.getESJDBCFieldValues();
		appendFieldValues(  record, vars,   fieldValueMetas,  addedFields);
		for(int i = 0;i < vars.size(); i ++)
		{
			VariableHandler.Variable var = vars.get(i);
			if(addedFields.get(var.getVariableName()) != null)
				continue;
			temp = jdbcResultSet.getValue(var.getVariableName());
			if(temp == null) {
				logger.warn("未指定绑定变量的值：{}",var.getVariableName());
			}
			param = new Param();
			param.setVariable(var);
			param.setIndex(var.getPosition()  +1);
			param.setValue(temp);
			param.setName(var.getVariableName());
			record.add(param);

		}
		return record;
	}
	@Override
	public String parallelBatchExecute() {
		int count = 0;
		ExecutorService service = importContext.buildThreadPool();
		List<Future> tasks = new ArrayList<Future>();
		int taskNo = 0;
		ImportCount totalCount = new ParallImportCount();
		Exception exception = null;
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		Object lastValue = null;
		TranErrorWrapper tranErrorWrapper = new TranErrorWrapper(importContext);
		int batchsize = importContext.getStoreBatchSize();
		try {
			TranSQLInfo sqlinfo = es2DBContext.getSqlInfo();
			Object temp = null;
			Param param = null;
			List<List<Param>> records = new ArrayList<List<Param>>();
			while (jdbcResultSet.next()) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
				if(lastValue == null)
					lastValue = importContext.max(currentValue,getLastValue());
				else{
					lastValue = importContext.max(lastValue,getLastValue());
				}

				Context context = new ContextImpl(importContext, jdbcResultSet, null);
				context.refactorData();
				if (context.isDrop()) {
					totalCount.increamentIgnoreTotalCount();
					continue;
				}
				List<Param> record = buildRecord(  context,  sqlinfo.getVars() );
				records.add(record);
				//						evalBuilk(this.jdbcResultSet, batchContext, writer, context, "index", clientInterface.isVersionUpper7());
				count++;
				if (count == batchsize) {

					count = 0;
					taskNo ++;
					Base2DBTaskCommandImpl taskCommand = new Base2DBTaskCommandImpl(sqlinfo.getSql(),totalCount,importContext,records,taskNo,totalCount.getJobNo());
					records = new ArrayList<List<Param>>();
					tasks.add(service.submit(new TaskCall(taskCommand,  tranErrorWrapper)));



				}

			}
			if (count > 0) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
//				if(this.error != null && !importContext.isContinueOnError()) {
//					throw error;
//				}
				taskNo ++;
				Base2DBTaskCommandImpl taskCommand = new Base2DBTaskCommandImpl(sqlinfo.getSql(),totalCount,importContext,records,taskNo,totalCount.getJobNo());
				tasks.add(service.submit(new TaskCall(taskCommand,tranErrorWrapper)));

				if(isPrintTaskLog())
					logger.info(new StringBuilder().append("submit tasks:").append(taskNo).toString());
			}
			else{
				if(isPrintTaskLog())
					logger.info(new StringBuilder().append("submit tasks:").append(taskNo).toString());
			}

		} catch (SQLException e) {
			exception = e;
			throw new ElasticSearchException(e);

		} catch (ElasticSearchException e) {
			exception = e;
			throw e;
		} catch (Exception e) {
			exception = e;
			throw new ElasticSearchException(e);
		}
		finally {
			waitTasksComplete(   tasks,  service,exception,  lastValue,totalCount ,tranErrorWrapper);
			totalCount.setJobEndTime(new Date());
		}

		return null;
	}

	@Override
	public String batchExecute() {
		int count = 0;
		String ret = null;
		int taskNo = 0;
		Exception exception = null;
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		Object lastValue = null;
		TranErrorWrapper tranErrorWrapper = new TranErrorWrapper(importContext);
		long start = System.currentTimeMillis();
		long istart = 0;
		long end = 0;
		long totalCount = 0;
		long ignoreTotalCount = 0;
		ImportCount importCount = new SerialImportCount();
		int batchsize = importContext.getStoreBatchSize();
		String refreshOption = importContext.getRefreshOption();
		try {
			istart = start;
			TranSQLInfo sqlinfo = es2DBContext.getSqlInfo();
			List<List<Param>> records = new ArrayList<List<Param>>();
			while (jdbcResultSet.next()) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
				if(lastValue == null)
					lastValue = importContext.max(currentValue,getLastValue());
				else{
					lastValue = importContext.max(lastValue,getLastValue());
				}
				Context context = new ContextImpl(importContext, jdbcResultSet, null);
				context.refactorData();
				if (context.isDrop()) {
					importCount.increamentIgnoreTotalCount();
					continue;
				}
				List<Param> record = buildRecord(  context,  sqlinfo.getVars() );
				records.add(record);
				count++;
				if (count == batchsize) {
					count = 0;
					taskNo ++;
					Base2DBTaskCommandImpl taskCommand = new Base2DBTaskCommandImpl(sqlinfo.getSql(),importCount,importContext,records,taskNo,importCount.getJobNo());
					records = new ArrayList<List<Param>>();
					ret = TaskCall.call(taskCommand);
					importContext.flushLastValue(lastValue);

					if(isPrintTaskLog())  {
						end = System.currentTimeMillis();
						logger.info(new StringBuilder().append("Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
								.append(",import ").append(batchsize).append(" records.").toString());
						istart = end;
					}
					totalCount += batchsize;


				}

			}
			if (count > 0) {
				if(!tranErrorWrapper.assertCondition()) {
					tranErrorWrapper.throwError();
				}
				taskNo ++;
				Base2DBTaskCommandImpl taskCommand = new Base2DBTaskCommandImpl(sqlinfo.getSql(),importCount,importContext,records,taskNo,importCount.getJobNo());
				ret = TaskCall.call(taskCommand);
				importContext.flushLastValue(lastValue);
				if(isPrintTaskLog())  {
					end = System.currentTimeMillis();
					logger.info(new StringBuilder().append("Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
							.append(",import ").append(count).append(" records.").toString());

				}
				totalCount += count;
			}
			if(isPrintTaskLog()) {
				end = System.currentTimeMillis();
				logger.info(new StringBuilder().append("Execute Tasks:").append(taskNo).append(",All Take time:").append((end - start)).append("ms")
						.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
						.append(ignoreTotalCount).append(" records.").toString());

			}
		}  catch (ElasticSearchException e) {
			exception = e;
			throw e;
		} catch (Exception e) {
			exception = e;
			throw new ElasticSearchException(e);
		}
		finally {
			if(!tranErrorWrapper.assertCondition(exception)){
				stop();
			}
			importCount.setJobEndTime(new Date());
		}

		return ret;
	}


	private void appendFieldValues(List<Param> record,
			List<VariableHandler.Variable> vars,
			List<FieldMeta> fieldValueMetas,
			Map<String, Object> addedFields) {
		if(fieldValueMetas ==  null || fieldValueMetas.size() == 0){
			return;
		}
		int i = 0;
		Param param = null;
		for(VariableHandler.Variable variable:vars){
			if(addedFields.containsKey(variable.getVariableName()))
				continue;
			for(FieldMeta fieldMeta:fieldValueMetas){
				if(variable.getVariableName().equals(fieldMeta.getEsFieldName())){
					param = new Param();
					param.setVariable(variable);
					param.setIndex(variable.getPosition() +1);
					param.setValue(fieldMeta.getValue());
					param.setName(variable.getVariableName());
					record.add(param);
//					statement.setObject(i +1,fieldMeta.getValue());
					addedFields.put(variable.getVariableName(),dummy);
					break;
				}
			}
		}
	}




}
