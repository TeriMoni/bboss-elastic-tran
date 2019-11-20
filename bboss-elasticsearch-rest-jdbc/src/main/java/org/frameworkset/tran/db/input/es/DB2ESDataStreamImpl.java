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

import org.frameworkset.tran.DataStream;
import org.frameworkset.tran.config.BaseImportConfig;
import org.frameworkset.tran.context.ImportContext;

/**
 * 数据库同步到Elasticsearch
 */
public class DB2ESDataStreamImpl extends DataStream{


//	private static Logger logger = LoggerFactory.getLogger(DataStream.class);


	protected ImportContext buildImportContext(BaseImportConfig importConfig){
		return new DB2ESImportContext(importConfig);
	}



}
