package org.frameworkset.tran.plugin.custom.output;
/**
 * Copyright 2008 biaoping.yin
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

import org.frameworkset.tran.BaseCommonRecordDataTran;
import org.frameworkset.tran.BaseDataTran;
import org.frameworkset.tran.TranResultSet;
import org.frameworkset.tran.context.ImportContext;
import org.frameworkset.tran.plugin.BasePlugin;
import org.frameworkset.tran.plugin.OutputPlugin;
import org.frameworkset.tran.schedule.Status;
import org.frameworkset.tran.schedule.TaskContext;

import java.util.concurrent.CountDownLatch;

/**
 * <p>Description: </p>
 * <p></p>
 * <p>Copyright (c) 2018</p>
 * @Date 2019/10/9 14:35
 * @author biaoping.yin
 * @version 1.0
 */
public class CustomOutputDataTranPlugin extends BasePlugin implements OutputPlugin {

	public CustomOutputDataTranPlugin(ImportContext importContext){
		super(importContext);


	}

	@Override
	public BaseDataTran createBaseDataTran(TaskContext taskContext, TranResultSet tranResultSet, CountDownLatch countDownLatch, Status currentStatus) {
		BaseCommonRecordDataTran baseCommonRecordDataTran = new CustomOutPutDataTran(taskContext, tranResultSet, importContext,countDownLatch, currentStatus);
		baseCommonRecordDataTran.initTran();
		return baseCommonRecordDataTran;
	}

	@Override
	public void afterInit() {

	}

	@Override
	public void beforeInit() {

	}

	@Override
	public void init() {

	}

	@Override
	public void destroy(boolean waitTranStop) {

	}









}
