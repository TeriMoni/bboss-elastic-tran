package org.frameworkset.tran;

import com.frameworkset.common.poolman.handle.ValueExchange;
import com.frameworkset.orm.annotation.BatchContext;
import com.frameworkset.orm.annotation.ESIndexWrapper;
import com.frameworkset.util.SimpleStringUtil;
import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.elasticsearch.ElasticSearchHelper;
import org.frameworkset.elasticsearch.client.*;
import org.frameworkset.tran.context.Context;
import org.frameworkset.tran.context.ContextImpl;
import org.frameworkset.tran.context.ImportContext;
import org.frameworkset.tran.db.input.es.JDBCGetVariableValue;
import org.frameworkset.tran.db.input.es.TaskCommandImpl;
import org.frameworkset.tran.metrics.ImportCount;
import org.frameworkset.tran.metrics.ParallImportCount;
import org.frameworkset.tran.metrics.SerialImportCount;
import org.frameworkset.tran.schedule.Status;
import org.frameworkset.tran.task.TaskCall;
import org.frameworkset.elasticsearch.serial.CharEscapeUtil;
import org.frameworkset.elasticsearch.template.ESUtil;
import org.frameworkset.soa.BBossStringWriter;
import org.frameworkset.util.annotations.DateFormateMeta;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class BaseElasticsearchDataTran extends BaseDataTran{
	private ClientInterface clientInterface;

	@Override
	public void logTaskStart(Logger logger) {
		logger.info(new StringBuilder().append("import data to IndexName[").append(importContext.getEsIndexWrapper().getIndex())
				.append("] IndexType[").append(importContext.getEsIndexWrapper().getType())
				.append("] start.").toString());
	}

	public BaseElasticsearchDataTran(TranResultSet jdbcResultSet, ImportContext importContext) {
		super(jdbcResultSet,importContext);
		clientInterface = ElasticSearchHelper.getRestClientUtil();
	}

	public BaseElasticsearchDataTran(TranResultSet jdbcResultSet,ImportContext importContext, String esCluster) {
		super(jdbcResultSet,importContext);
		clientInterface = ElasticSearchHelper.getRestClientUtil(esCluster);
	}

//	public BaseDataTran(String esCluster) {
//		clientInterface = ElasticSearchHelper.getRestClientUtil(esCluster);
//	}



	/**
	 * 并行批处理导入

	 * @return
	 */
	public String parallelBatchExecute( ){
		int count = 0;
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		String ret = null;
		ExecutorService	service = importContext.buildThreadPool();
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

			BatchContext batchContext = new BatchContext();
			while (true) {
				if(!tranErrorWrapper.assertCondition()) {
					jdbcResultSet.stop();
					tranErrorWrapper.throwError();
				}
				Boolean hasNext = jdbcResultSet.next();
				if(hasNext == null){//强制flush操作
					if (count > 0) {
						writer.flush();
						String datas = builder.toString();
						builder.setLength(0);
						writer.close();
						writer = new BBossStringWriter(builder);
						taskNo ++;
						TaskCommandImpl taskCommand = new TaskCommandImpl(totalCount,importContext,count,taskNo,totalCount.getJobNo());
						count = 0;
						taskCommand.setClientInterface(clientInterface);
						taskCommand.setRefreshOption(importContext.getRefreshOption());
						taskCommand.setDatas(datas);
						tasks.add(service.submit(new TaskCall(taskCommand,  tranErrorWrapper)));
					}
					continue;
				}
				else if(!hasNext.booleanValue())
					break;

				if(lastValue == null)
					lastValue = importContext.max(currentValue,getLastValue());
				else{
					lastValue = importContext.max(lastValue,getLastValue());
				}

				Context context = new ContextImpl(importContext, jdbcResultSet, batchContext);
				context.refactorData();
				if (context.isDrop()) {
					totalCount.increamentIgnoreTotalCount();
					continue;
				}
				evalBuilk(this.jdbcResultSet,  batchContext,writer, context, "index",clientInterface.isVersionUpper7());
				count++;
				if (count >= batchsize) {
					writer.flush();
					String datas = builder.toString();
					builder.setLength(0);
					writer.close();
					writer = new BBossStringWriter(builder);
					taskNo ++;
					TaskCommandImpl taskCommand = new TaskCommandImpl(totalCount,importContext,count,taskNo,totalCount.getJobNo());
					count = 0;
					taskCommand.setClientInterface(clientInterface);
					taskCommand.setRefreshOption(importContext.getRefreshOption());
					taskCommand.setDatas(datas);
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
				writer.flush();
				String datas = builder.toString();
				taskNo ++;
				TaskCommandImpl taskCommand = new TaskCommandImpl(totalCount,importContext,count,taskNo,totalCount.getJobNo());
				taskCommand.setClientInterface(clientInterface);
				taskCommand.setRefreshOption(importContext.getRefreshOption());
				taskCommand.setDatas(datas);
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
			try {
				writer.close();
			} catch (Exception e) {

			}
			totalCount.setJobEndTime(new Date());
		}

		return ret;
	}
	/**
	 * 串行批处理导入
	 * @return
	 */
	public String batchExecute(  ){
		int count = 0;
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		String ret = null;
		int taskNo = 0;
		Exception exception = null;
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		Object lastValue = null;
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
			BatchContext batchContext = new BatchContext();
			while (true) {
				Boolean hasNext = jdbcResultSet.next();
				if(hasNext == null){
					if(count > 0) {
						writer.flush();
						String datas = builder.toString();
						builder.setLength(0);
						writer.close();
						writer = new BBossStringWriter(builder);


						taskNo++;
						TaskCommandImpl taskCommand = new TaskCommandImpl(importCount, importContext, count, taskNo, importCount.getJobNo());
						int temp = count;
						count = 0;
						taskCommand.setClientInterface(clientInterface);
						taskCommand.setRefreshOption(refreshOption);
						taskCommand.setDatas(datas);
						ret = TaskCall.call(taskCommand);
						importContext.flushLastValue(lastValue);


						if (isPrintTaskLog()) {
							end = System.currentTimeMillis();
							logger.info(new StringBuilder().append("Force flush datas Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
									.append(",import ").append(temp).append(" records.").toString());
							istart = end;
						}
						totalCount += temp;
					}
					continue;
				}
				else if(!hasNext.booleanValue()){
					break;
				}
				if(lastValue == null)
					lastValue = importContext.max(currentValue,getLastValue());
				else{
					lastValue = importContext.max(lastValue,getLastValue());
				}
				Context context = new ContextImpl(importContext, jdbcResultSet, batchContext);
				context.refactorData();
				if (context.isDrop()) {
					importCount.increamentIgnoreTotalCount();
					continue;
				}
				evalBuilk(  this.jdbcResultSet,batchContext,writer,   context, "index",clientInterface.isVersionUpper7());
				count++;
				if (count >= batchsize) {
					writer.flush();
					String datas = builder.toString();
					builder.setLength(0);
					writer.close();
					writer = new BBossStringWriter(builder);


					taskNo ++;
					TaskCommandImpl taskCommand = new TaskCommandImpl(importCount,importContext,count,taskNo,importCount.getJobNo());
					count = 0;
					taskCommand.setClientInterface(clientInterface);
					taskCommand.setRefreshOption(refreshOption);
					taskCommand.setDatas(datas);
					ret = TaskCall.call(taskCommand);
					importContext.flushLastValue(lastValue);


					if(isPrintTaskLog())  {
						end = System.currentTimeMillis();
						logger.info(new StringBuilder().append("Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
								.append(",import ").append(batchsize).append(" records.").toString());
						istart = end;
					}
					totalCount += count;


				}

			}
			if (count > 0) {
				writer.flush();
				String datas = builder.toString();

				taskNo ++;
				TaskCommandImpl taskCommand = new TaskCommandImpl(importCount,importContext,count,taskNo,importCount.getJobNo());
				taskCommand.setClientInterface(clientInterface);
				taskCommand.setRefreshOption(refreshOption);
				taskCommand.setDatas(datas);
				ret = TaskCall.call(taskCommand);
				importContext.flushLastValue(lastValue);
				if(isPrintTaskLog())  {
					end = System.currentTimeMillis();
					logger.info(new StringBuilder().append("Task[").append(taskNo).append("] complete,take time:").append((end - istart)).append("ms")
							.append(",import ").append(count).append(" records,IgnoreTotalCount ")
							.append(ignoreTotalCount).append(" records.").toString());

				}
				totalCount += count;
			}
			if(isPrintTaskLog()) {
				end = System.currentTimeMillis();
				logger.info(new StringBuilder().append("Execute Tasks:").append(taskNo).append(",All Take time:").append((end - start)).append("ms")
						.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
						.append(ignoreTotalCount).append(" records.").toString());

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

			if(!TranErrorWrapper.assertCondition(exception ,importContext)){
				stop();
			}
			try {
				writer.close();
			} catch (Exception e) {

			}
			importCount.setJobEndTime(new Date());
		}

		return ret;
	}

	public String serialExecute(  ){
		String refreshOption = importContext.getRefreshOption();
		StringBuilder builder = new StringBuilder();
		BBossStringWriter writer = new BBossStringWriter(builder);
		Object lastValue = null;
		Exception exception = null;
		long start = System.currentTimeMillis();
		Status currentStatus = importContext.getCurrentStatus();
		Object currentValue = currentStatus != null? currentStatus.getLastValue():null;
		long totalCount = 0;
		ImportCount importCount = new SerialImportCount();
		long ignoreTotalCount = 0;
		try {
			BatchContext batchContext =  new BatchContext();
			while (true) {
				Boolean hasNext = jdbcResultSet.next();
				if(hasNext == null){ //强制flush数据
					writer.flush();
					String ret = null;
					if(builder.length() > 0) {

						TaskCommandImpl taskCommand = new TaskCommandImpl(importCount,importContext,totalCount,1,importCount.getJobNo());
						taskCommand.setClientInterface(clientInterface);
						taskCommand.setRefreshOption(refreshOption);
						taskCommand.setDatas(builder.toString());
						builder.setLength(0);
						ret = TaskCall.call(taskCommand);
					}
					else{
						ret = "{\"took\":0,\"errors\":false}";
					}
					importContext.flushLastValue(lastValue);
					if(isPrintTaskLog()) {

						long end = System.currentTimeMillis();
						logger.info(new StringBuilder().append("Force flush datas Take time:").append((end - start)).append("ms")
								.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
								.append(ignoreTotalCount).append(" records.").toString());

					}
					continue;
				}
				else if(!hasNext.booleanValue()){
					break;
				}
				try {
					if(lastValue == null)
						lastValue = importContext.max(currentValue,getLastValue());
					else{
						lastValue = importContext.max(lastValue,getLastValue());
					}
					Context context = new ContextImpl(importContext, jdbcResultSet, batchContext);
					context.refactorData();
					if (context.isDrop()) {
						importCount.increamentIgnoreTotalCount();
						continue;
					}
					evalBuilk(this.jdbcResultSet,  batchContext,writer,  context,  "index",clientInterface.isVersionUpper7());
					totalCount ++;
				} catch (Exception e) {
					throw new ElasticSearchException(e);
				}

			}
			writer.flush();
			String ret = null;
			if(builder.length() > 0) {

				TaskCommandImpl taskCommand = new TaskCommandImpl(importCount,importContext,totalCount,1,importCount.getJobNo());
				taskCommand.setClientInterface(clientInterface);
				taskCommand.setRefreshOption(refreshOption);
				taskCommand.setDatas(builder.toString());
				ret = TaskCall.call(taskCommand);
			}
			else{
				ret = "{\"took\":0,\"errors\":false}";
			}
			importContext.flushLastValue(lastValue);
			if(isPrintTaskLog()) {

				long end = System.currentTimeMillis();
				logger.info(new StringBuilder().append("All Take time:").append((end - start)).append("ms")
						.append(",Import total ").append(totalCount).append(" records,IgnoreTotalCount ")
						.append(ignoreTotalCount).append(" records.").toString());

			}
			return ret;
		} catch (ElasticSearchException e) {
			exception = e;
			throw e;
		} catch (Exception e) {
			exception = e;
			throw new ElasticSearchException(e);
		}
		finally {
			if(!TranErrorWrapper.assertCondition(exception ,importContext)){
				stop();
			}
			importCount.setJobEndTime(new Date());
		}
	}
	public String tran(String indexName,String indexType) throws ElasticSearchException{
		ESIndexWrapper esIndexWrapper = new ESIndexWrapper(indexName,indexType);
		importContext.setEsIndexWrapper(esIndexWrapper);
		return tran();
	}



	public static void buildMeta(Context context,Writer writer ,String action,boolean upper7) throws Exception {

		Object id = context.getEsId();
		Object parentId = context.getParentId();
		Object routing = context.getRouting();

		Object esRetryOnConflict = context.getEsRetryOnConflict();


		buildMeta( context, writer ,      action,  id,  parentId,routing,esRetryOnConflict,upper7);
	}

	public static void buildMeta(Context context, Writer writer , String action,
								 Object id, Object parentId, Object routing, Object esRetryOnConflict, boolean upper7) throws Exception {
		ESIndexWrapper esIndexWrapper = context.getESIndexWrapper();

		JDBCGetVariableValue jdbcGetVariableValue = new JDBCGetVariableValue(context);

		if(id != null) {
			writer.write("{ \"");
			writer.write(action);
			writer.write("\" : { \"_index\" : \"");

			if (esIndexWrapper == null ) {
				throw new ESDataImportException(" ESIndex not seted." );
			}
			BuildTool.buildIndiceName(esIndexWrapper,writer,jdbcGetVariableValue);

			writer.write("\"");
			if(!upper7) {
				writer.write(", \"_type\" : \"");
				if (esIndexWrapper == null ) {
					throw new ESDataImportException(" ESIndex type not seted." );
				}
				BuildTool.buildIndiceType(esIndexWrapper,writer,jdbcGetVariableValue);
				writer.write("\"");
			}
			writer.write(", \"_id\" : ");
			BuildTool.buildId(id,writer,true);
			if(parentId != null){
				writer.write(", \"parent\" : ");
				BuildTool.buildId(parentId,writer,true);
			}
			if(routing != null){
				if(!upper7) {
					writer.write(", \"_routing\" : ");
				}
				else{
					writer.write(", \"routing\" : ");
				}
				BuildTool.buildId(routing,writer,true);
			}

//			if(action.equals("update"))
//			{
			if (esRetryOnConflict != null) {
				writer.write(",\"_retry_on_conflict\":");
				writer.write(String.valueOf(esRetryOnConflict));
			}
			Object version = context.getVersion();

			if (version != null) {

				writer.write(",\"_version\":");

				writer.write(String.valueOf(version));

			}

			Object versionType = context.getEsVersionType();
			if(versionType != null) {
				writer.write(",\"_version_type\":\"");
				writer.write(String.valueOf(versionType));
				writer.write("\"");
			}



			writer.write(" } }\n");
		}
		else {

			writer.write("{ \"");
			writer.write(action);
			writer.write("\" : { \"_index\" : \"");
			if (esIndexWrapper == null ) {
				throw new ESDataImportException(" ESIndex not seted." );
			}
			BuildTool.buildIndiceName(esIndexWrapper,writer,jdbcGetVariableValue);
			writer.write("\"");
			if(!upper7) {
				writer.write(", \"_type\" : \"");
				if (esIndexWrapper == null ) {
					throw new ESDataImportException(" ESIndex type not seted." );
				}
				BuildTool.buildIndiceType(esIndexWrapper,writer,jdbcGetVariableValue);
				writer.write("\"");

			}

			if(parentId != null){
				writer.write(", \"parent\" : ");
				BuildTool.buildId(parentId,writer,true);
			}
			if(routing != null){

				if(!upper7) {
					writer.write(", \"_routing\" : ");
				}
				else{
					writer.write(", \"routing\" : ");
				}
				BuildTool.buildId(routing,writer,true);
			}
//			if(action.equals("update"))
//			{

			if (esRetryOnConflict != null) {
				writer.write(",\"_retry_on_conflict\":");
				writer.write(String.valueOf(esRetryOnConflict));
			}
			Object version = context.getVersion();
			if (version != null) {

				writer.write(",\"_version\":");

				writer.write(String.valueOf(version));

			}

			Object versionType = context.getEsVersionType();
			if(versionType != null) {
				writer.write(",\"_version_type\":\"");
				writer.write(String.valueOf(versionType));
				writer.write("\"");
			}
			writer.write(" } }\n");
		}
	}

	public  void evalBuilk(TranResultSet jdbcResultSet,BatchContext batchContext, Writer writer, Context context, String action, boolean upper7) throws Exception {

		buildMeta( context, writer ,     action,  upper7);

		if(!action.equals("update")) {
//				SerialUtil.object2json(param,writer);
			serialResult(  writer,context);
		}
		else
		{

			writer.write("{\"doc\":");
			serialResult(  writer,context);
			if(context.getEsDocAsUpsert() != null){
				writer.write(",\"doc_as_upsert\":");
				writer.write(String.valueOf(context.getEsDocAsUpsert()));
			}

			if(context.getEsReturnSource() != null){
				writer.write(",\"_source\":");
				writer.write(String.valueOf(context.getEsReturnSource()));
			}
			writer.write("}\n");
		}


	}

	private  void serialResult( Writer writer,  Context context) throws Exception {

		TranMeta metaData = context.getMetaData();
		int counts = metaData != null ?metaData.getColumnCount():0;
		writer.write("{");
		Boolean useJavaName = context.getUseJavaName();
		if(useJavaName == null)
			useJavaName = true;

		Boolean useLowcase = context.getUseLowcase();


		if(useJavaName == null) {
			useJavaName = false;
		}
		if(useLowcase == null)
		{
			useLowcase = false;
		}
		boolean hasSeted = false;

		Map<String,Object> addedFields = new HashMap<String,Object>();

		List<FieldMeta> fieldValueMetas = context.getFieldValues();//context优先级高于，全局配置，全局配置高于字段值
		hasSeted = appendFieldValues(  writer,   context, fieldValueMetas,  hasSeted,addedFields);
		fieldValueMetas = context.getESJDBCFieldValues();
		hasSeted = appendFieldValues(  writer,   context, fieldValueMetas,  hasSeted,addedFields);
		for(int i =0; i < counts; i++)
		{
			String colName = metaData.getColumnLabelByIndex(i);
			if(colName.equals("_id")){
				if(logger.isDebugEnabled()){
					logger.debug("Field [_id] is a metadata field and cannot be added inside a document. Use the index API request parameters.");
				}
				continue;
			}
			int sqlType = metaData.getColumnTypeByIndex(i);
//			if("ROWNUM__".equals(colName))//去掉oracle的行伪列
//				continue;
			String javaName = null;
			FieldMeta fieldMeta = context.getMappingName(colName);
			if(fieldMeta != null) {
				if(fieldMeta.getIgnore() != null && fieldMeta.getIgnore() == true)
					continue;
				javaName = fieldMeta.getEsFieldName();
			}
			else {
				if(useJavaName) {
					javaName = metaData.getColumnJavaNameByIndex(i);
				}
				else{
					javaName =  !useLowcase ?colName:metaData.getColumnLabelLowerByIndex(i);
				}
			}
			if(javaName == null){
				javaName = colName;
			}
			if(addedFields.containsKey(javaName)){
				continue;
			}
			Object value = context.getValue(     i,  colName,sqlType);
			if(value == null && importContext.isIgnoreNullValueField()){
				continue;
			}
			if(hasSeted )
				writer.write(",");
			else
				hasSeted = true;

			writer.write("\"");
			writer.write(javaName);
			writer.write("\":");
//			int colType = metaData.getColumnTypeByIndex(i);

			if(value != null) {
				if (value instanceof String) {
					writer.write("\"");
					CharEscapeUtil charEscapeUtil = new CharEscapeUtil(writer);
					charEscapeUtil.writeString((String) value, true);
					writer.write("\"");
				} else if (value instanceof Date) {
					DateFormat dateFormat = null;
					if(fieldMeta != null){
						DateFormateMeta dateFormateMeta = fieldMeta.getDateFormateMeta();
						if(dateFormateMeta != null){
							dateFormat = dateFormateMeta.toDateFormat();
						}
					}
					if(dateFormat == null)
						dateFormat = context.getDateFormat();
					String dataStr = ESUtil.getDate((Date) value,dateFormat);
					writer.write("\"");
					writer.write(dataStr);
					writer.write("\"");
				}
				else if(value instanceof Clob)
				{
					String dataStr = ValueExchange.getStringFromClob((Clob)value);
					writer.write("\"");
					CharEscapeUtil charEscapeUtil = new CharEscapeUtil(writer);
					charEscapeUtil.writeString(dataStr, true);
					writer.write("\"");

				}
				else if(value instanceof Blob){
					String dataStr = ValueExchange.getStringFromBlob((Blob)value);
					writer.write("\"");
					CharEscapeUtil charEscapeUtil = new CharEscapeUtil(writer);
					charEscapeUtil.writeString(dataStr, true);
					writer.write("\"");
				}
				else {
					SimpleStringUtil.object2json(value,writer);//					writer.write(String.valueOf(value));
				}
			}
			else{
				writer.write("null");
			}

		}

		writer.write("}\n");
	}
	private  boolean appendFieldValues(Writer writer,Context context,
										  List<FieldMeta> fieldValueMetas,boolean hasSeted,Map<String,Object> addedFields) throws IOException {
		if(fieldValueMetas != null && fieldValueMetas.size() > 0){
			for(int i =0; i < fieldValueMetas.size(); i++)
			{

				FieldMeta fieldMeta = fieldValueMetas.get(i);
				String javaName = fieldMeta.getEsFieldName();
				if(addedFields.containsKey(javaName)) {
					if(logger.isInfoEnabled()){
						logger.info(new StringBuilder().append("Ignore adding duplicate field[")
								.append(javaName).append("] value[")
								.append(fieldMeta.getValue())
								.append("].").toString());
					}
					continue;
				}
				Object value = fieldMeta.getValue();
//				if(value == null)
//					continue;
				if(hasSeted)
					writer.write(",");
				else{
					hasSeted = true;
				}

				writer.write("\"");
				writer.write(javaName);
				writer.write("\":");

				if(value != null) {
					if (value instanceof String) {
						writer.write("\"");
						CharEscapeUtil charEscapeUtil = new CharEscapeUtil(writer);
						charEscapeUtil.writeString((String) value, true);
						writer.write("\"");
					} else if (value instanceof Date) {
						DateFormat dateFormat = null;
						if(fieldMeta != null){
							DateFormateMeta dateFormateMeta = fieldMeta.getDateFormateMeta();
							if(dateFormateMeta != null){
								dateFormat = dateFormateMeta.toDateFormat();
							}
						}
						if(dateFormat == null)
							dateFormat = context.getDateFormat();
						String dataStr = ESUtil.getDate((Date) value,dateFormat);
						writer.write("\"");
						writer.write(dataStr);
						writer.write("\"");
					}
					else if(isBasePrimaryType(value.getClass())){
						writer.write(String.valueOf(value));
					}
					else {
						SimpleStringUtil.object2json(value,writer);
					}
				}
				else{
					writer.write("null");
				}
				addedFields.put(javaName,dummy);

			}
		}
		return hasSeted;
	}
	public static final Class[] basePrimaryTypes = new Class[]{Integer.TYPE, Long.TYPE,
								Boolean.TYPE, Float.TYPE, Short.TYPE, Double.TYPE,
								Character.TYPE, Byte.TYPE, BigInteger.class, BigDecimal.class};

	public static boolean isBasePrimaryType(Class type) {
		if (!type.isArray()) {
			if (type.isEnum()) {
				return true;
			} else {
				Class[] var1 = basePrimaryTypes;
				int var2 = var1.length;

				for(int var3 = 0; var3 < var2; ++var3) {
					Class primaryType = var1[var3];
					if (primaryType.isAssignableFrom(type)) {
						return true;
					}
				}

				return false;
			}
		} else {
			return false;
		}
	}



}
