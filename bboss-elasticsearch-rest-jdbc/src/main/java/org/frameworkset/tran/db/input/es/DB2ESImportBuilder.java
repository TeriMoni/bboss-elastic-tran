package org.frameworkset.tran.db.input.es;/*
 *  Copyright 2008 biaoping.yin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import com.frameworkset.util.SimpleStringUtil;
import org.frameworkset.tran.DataStream;
import org.frameworkset.tran.ExportResultHandler;
import org.frameworkset.tran.WrapedExportResultHandler;
import org.frameworkset.tran.config.BaseImportBuilder;
import org.frameworkset.tran.db.DBImportConfig;
import org.frameworkset.tran.es.ESExportResultHandler;

public class DB2ESImportBuilder extends BaseImportBuilder {
	protected String sqlFilepath;
	protected String sql;
	protected String sqlName;

	protected DB2ESImportBuilder(){

	}





	public DB2ESImportBuilder setShowSql(boolean showSql) {
		_setShowSql(showSql);

		return this;
	}




	public String getSql() {
		return sql;
	}




	public static DB2ESImportBuilder newInstance(){
		return new DB2ESImportBuilder();
	}





	public DB2ESImportBuilder setSql(String sql) {
		this.sql = sql;
		return this;
	}

	public DataStream builder(){
		super.builderConfig();
		try {
			if(logger.isInfoEnabled()) {
				logger.info("DB2ES Import Configs:");
				logger.info(this.toString());
			}
		}
		catch (Exception e){

		}
		DBImportConfig importConfig = new DBImportConfig();
//		esjdbc.setImportBuilder(this);
		super.buildImportConfig(importConfig);
//		esjdbcResultSet.setMetaData(statementInfo.getMeta());
//		esjdbcResultSet.setResultSet(resultSet);

		importConfig.setSqlFilepath(this.sqlFilepath);
		importConfig.setSqlName(sqlName);
		if(SimpleStringUtil.isNotEmpty(sql))
			importConfig.setSql(this.sql);
		DB2ESDataStreamImpl  dataStream = new DB2ESDataStreamImpl();
		dataStream.setImportConfig(importConfig);
		dataStream.setConfigString(this.toString());
		dataStream.init();
		return dataStream;
	}




	public DB2ESImportBuilder setSqlFilepath(String sqlFilepath) {
		this.sqlFilepath = sqlFilepath;
		return this;
	}

	public String getSqlName() {
		return sqlName;
	}

	public DB2ESImportBuilder setSqlName(String sqlName) {
		this.sqlName = sqlName;
		return this;
	}

	@Override
	protected WrapedExportResultHandler buildExportResultHandler(ExportResultHandler exportResultHandler) {
		ESExportResultHandler db2ESExportResultHandler = new ESExportResultHandler(exportResultHandler);
		return db2ESExportResultHandler;
	}


//	private IndexPattern splitIndexName(String indexPattern){
//		int idx = indexPattern.indexOf("{");
//		int end = -1;
//		if(idx > 0){
//			end = indexPattern.indexOf("}");
//			IndexPattern _indexPattern = new IndexPattern();
//			_indexPattern.setIndexPrefix(indexPattern.substring(0,idx));
//			_indexPattern.setDateFormat(indexPattern.substring(idx + 1,end));
//			if(end < indexPattern.length()){
//				_indexPattern.setIndexEnd(indexPattern.substring(end+1));
//			}
//			return _indexPattern;
//		}
//		return null;
//
//
//	}


}
