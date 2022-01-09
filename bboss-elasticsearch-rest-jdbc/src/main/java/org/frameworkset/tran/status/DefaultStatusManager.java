package org.frameworkset.tran.status;
/**
 * Copyright 2020 bboss
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.frameworkset.common.poolman.SQLExecutor;
import org.frameworkset.tran.DataTranPlugin;
import org.frameworkset.tran.schedule.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Description: </p>
 * <p></p>
 * <p>Copyright (c) 2020</p>
 * @Date 2021/7/25 10:26
 * @author biaoping.yinq
 * @version 1.0
 */
public class DefaultStatusManager extends BaseStatusManager{
	private static Logger logger = LoggerFactory.getLogger(DefaultStatusManager.class);

	public DefaultStatusManager(String statusDbname, String updateSQL, int lastValueType, DataTranPlugin dataTranPlugin) {
		super(statusDbname, updateSQL, lastValueType,dataTranPlugin);
	}
	public void putStatus(Status currentStatus) throws Exception{

		SQLExecutor.updateWithDBName(statusDbname, updateSQL, currentStatus.getTime(), currentStatus.getLastValue(),
				lastValueType, currentStatus.getFilePath(), currentStatus.getRelativeParentDir(),currentStatus.getFileId(),
				currentStatus.getStatus(), currentStatus.getId());

	}
	public void init(){

	}
	public void flushStatus(){

	}
	protected void _putStatus(Status currentStatus){


	}
	protected void _flushStatus() throws Exception {

	}
}
