package org.frameworkset.tran.es.output;

import org.frameworkset.tran.context.ImportContext;
import org.frameworkset.tran.*;

import java.util.concurrent.CountDownLatch;

public abstract class AsynESOutPutDataTran<T> extends BaseElasticsearchDataTran {
	protected AsynTranResultSet esTranResultSet;
	private CountDownLatch countDownLatch;

	public AsynESOutPutDataTran(TranResultSet jdbcResultSet, ImportContext importContext, String esCluster, CountDownLatch countDownLatch) {
		super(jdbcResultSet, importContext, esCluster);
		this.countDownLatch = countDownLatch;
	}

	protected void init(){
		super.init();
		esTranResultSet = (AsynTranResultSet)jdbcResultSet;

	}


	public AsynESOutPutDataTran(TranResultSet jdbcResultSet, ImportContext importContext) {
		super(jdbcResultSet,importContext);
	}
	public AsynESOutPutDataTran(TranResultSet jdbcResultSet, ImportContext importContext,String cluster) {
		super(jdbcResultSet,importContext,cluster);
	}
	public AsynESOutPutDataTran(TranResultSet jdbcResultSet, ImportContext importContext, CountDownLatch countDownLatch) {
		super(jdbcResultSet,importContext);
		this.countDownLatch = countDownLatch;
	}
//	public void appendData(ESDatas datas){
//		esTranResultSet.appendData(new ESDatasWraper(datas));
//	}
	public abstract void appendInData(T data);
	protected void appendData(Data datas){
		esTranResultSet.appendData(datas);
	}


	public void stop(){
		esTranResultSet.stop();
		super.stop();
	}

	@Override
	public String tran() throws ESDataImportException {
		try {
			return super.tran();
		}
		finally {
			if(this.countDownLatch != null)
				countDownLatch.countDown();
		}
	}
}
