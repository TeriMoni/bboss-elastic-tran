package org.frameworkset.tran;

import org.frameworkset.tran.context.Context;
import org.frameworkset.tran.context.ImportContext;
import org.frameworkset.tran.record.RecordColumnInfo;
import org.frameworkset.tran.record.SplitKeys;
import org.frameworkset.tran.schedule.Status;
import org.frameworkset.tran.schedule.TaskContext;
import org.frameworkset.util.annotations.DateFormateMeta;

import java.text.DateFormat;
import java.util.*;

public abstract class BaseCommonRecordDataTran extends BaseDataTran{

	public BaseCommonRecordDataTran(TaskContext taskContext, TranResultSet jdbcResultSet, ImportContext importContext, ImportContext targetImportContext, Status currentStatus) {
		super(taskContext, jdbcResultSet, importContext, targetImportContext,  currentStatus);
	}

	protected void logColumnsInfo(){
		if(logger.isDebugEnabled())
			logger.debug("Export Columns is null,you can set Export Columns in importconfig or not.");
//				throw new DataImportException("Export Columns is null,Please set Export Columns in importconfig.");
	}
	protected CommonRecord buildRecord(Context context){

		String[] columns = targetImportContext.getExportColumns();
		//如果采用结果集中的字段集，就需要考虑全局添加或者记录级别添加的字段，通过配置设置导出的字段集不需要考虑全局添加或者记录级别添加的字段
		boolean useResultKeys = false;
		//记录切割时，每条记录对应切割出来的字段信息，切割记录
		String[] splitColumns = null;
		if (columns == null){
			Object  keys = jdbcResultSet.getKeys();
			if(keys != null) {
				boolean isSplitKeys = keys instanceof SplitKeys;
				if(!isSplitKeys) {
					useResultKeys = true;
					if (keys instanceof Set) {
						Set<String> _keys = (Set<String>) keys;
						columns = _keys.toArray(new String[_keys.size()]);
					} else {
						columns = (String[]) keys;
					}
				}
				else{
					SplitKeys splitKeys = (SplitKeys)keys;
					if(splitKeys.getBaseKeys() != null){
						Object baseKeys = splitKeys.getBaseKeys();
						useResultKeys = true;
						if (baseKeys instanceof Set) {
							Set<String> _keys = (Set<String>) baseKeys;
							columns = _keys.toArray(new String[_keys.size()]);
						} else {
							columns = (String[]) baseKeys;
						}
					}
					splitColumns = splitKeys.getSplitKeys();

				}
			}
			else{
				logColumnsInfo();
			}
		}
		else{
			Object  keys = jdbcResultSet.getKeys();
			if(keys != null) {
				boolean isSplitKeys = keys instanceof SplitKeys;
				if(isSplitKeys) {
					SplitKeys splitKeys = (SplitKeys) keys;

					splitColumns = splitKeys.getSplitKeys();
				}

			}
		}
		TranMeta metaData = context.getMetaData();
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
		Object temp = null;
		CommonRecord dbRecord = new CommonRecord();

		Map<String,Object> addedFields = new HashMap<String,Object>();
		//计算记录级别字段配置值
		List<FieldMeta> fieldValueMetas = context.getFieldValues();//context优先级高于splitColumns,splitColumns高于全局配置，全局配置高于数据源级别字段值

		appendFieldValues( dbRecord, columns,    fieldValueMetas,  addedFields, useResultKeys);
		//计算记录切割字段
		appendSplitFieldValues(dbRecord,
				 splitColumns,
				 addedFields);
		//计算全局级别字段配置值
		fieldValueMetas = context.getESJDBCFieldValues();//全局配置
		appendFieldValues(  dbRecord, columns,   fieldValueMetas,  addedFields,  useResultKeys);
		//计算数据源级别字段值
		String varName = null;
		String colName = null;
		String splitFieldName = context.getImportContext().getSplitFieldName();
		for(int i = 0;columns !=null && i < columns.length; i ++)
		{
			colName = columns[i];
			if(splitFieldName != null && !splitFieldName.equals("") && splitFieldName.equals(colName)){ //忽略切割字段
				continue;
			}

//			if(addedFields.get(colName) != null)
//				continue;
			//变量名称处理
			FieldMeta fieldMeta = context.getMappingName(colName);
			if(fieldMeta != null) {
				if(fieldMeta.getIgnore() != null && fieldMeta.getIgnore() == true)
					continue;
				varName = fieldMeta.getEsFieldName();
			}
			else if(useResultKeys && metaData != null && metaData.getColumnCount() > 0 ){
				if(useJavaName) {
					varName = metaData.getColumnJavaNameByIndex(i);
				}
				else{
					varName =  !useLowcase ?colName:metaData.getColumnLabelLowerByIndex(i);
				}
			}
			else{
				varName = colName;
			}
			if(addedFields.get(varName) != null)
				continue;
			int sqlType = useResultKeys && metaData != null?metaData.getColumnTypeByIndex(i):-1;
			temp = jdbcResultSet.getValue(colName,sqlType);
			RecordColumnInfo recordColumnInfo = null;
			if(temp == null) {
				if(logger.isWarnEnabled())
					logger.warn("未指定绑定变量{}的值！",varName);
			}
			else if (temp instanceof Date){
				DateFormat dateFormat = null;
				if(fieldMeta != null){
					DateFormateMeta dateFormateMeta = fieldMeta.getDateFormateMeta();
					if(dateFormateMeta != null){
						dateFormat = dateFormateMeta.toDateFormat();
					}
				}
				if(dateFormat == null)
					dateFormat = context.getDateFormat();
				recordColumnInfo = new RecordColumnInfo();
				recordColumnInfo.setDataTag(true);
				recordColumnInfo.setDateFormat(dateFormat);
			}
			dbRecord.addData(varName,temp,recordColumnInfo);


		}


		return dbRecord;
	}
	protected void appendFieldValues(CommonRecord record,
								   String[] columns ,
								   List<FieldMeta> fieldValueMetas,
								   Map<String, Object> addedFields, boolean useResultKeys) {
		if(fieldValueMetas ==  null || fieldValueMetas.size() == 0){
			return;
		}

		if(columns != null && columns.length > 0) {
			for (FieldMeta fieldMeta : fieldValueMetas) {
				String fieldName = fieldMeta.getEsFieldName();
				if (addedFields.containsKey(fieldName))
					continue;
				boolean matched = false;
				for (String name : columns) {
					if (name.equals(fieldName)) {
						record.addData(name, fieldMeta.getValue());
						addedFields.put(name, dummy);
						matched = true;
						break;
					}
				}
				if (useResultKeys && !matched) {
					record.addData(fieldName, fieldMeta.getValue());
					addedFields.put(fieldName, dummy);
				}
			}
		}
		else{ //hbase之类的数据同步工具，数据都是在datarefactor接口中封装处理，columns信息不存在，直接用fieldValueMetas即可
			for (FieldMeta fieldMeta : fieldValueMetas) {
				String fieldName = fieldMeta.getEsFieldName();
				if (addedFields.containsKey(fieldName))
					continue;
				record.addData(fieldName, fieldMeta.getValue());
				addedFields.put(fieldName, dummy);

			}
		}
	}


}
