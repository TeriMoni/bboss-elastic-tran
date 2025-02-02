package org.frameworkset.tran.output.fileftp;

import com.frameworkset.util.SimpleStringUtil;
import org.frameworkset.tran.*;
import org.frameworkset.tran.context.Context;
import org.frameworkset.tran.context.ImportContext;
import org.frameworkset.tran.input.file.GenFileInfo;
import org.frameworkset.tran.metrics.ImportCount;
import org.frameworkset.tran.metrics.JobTaskMetrics;
import org.frameworkset.tran.plugin.file.output.FileOutputConfig;
import org.frameworkset.tran.schedule.JobExecuteMetric;
import org.frameworkset.tran.schedule.Status;
import org.frameworkset.tran.schedule.TaskContext;
import org.frameworkset.tran.task.BaseParrelTranCommand;
import org.frameworkset.tran.task.BaseSerialTranCommand;
import org.frameworkset.tran.task.StringTranJob;
import org.frameworkset.tran.task.TaskCall;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class FileFtpOutPutDataTran extends BaseCommonRecordDataTran {

	protected FileOutputConfig fileOutputConfig;
//	protected String fileName;
//	protected String remoteFileName;
	protected FileTransfer fileTransfer;
	protected String path;
	protected int fileSeq = 1;

	protected JobCountDownLatch countDownLatch;
	@Override
	public void logTaskStart(Logger logger) {
//		StringBuilder builder = new StringBuilder().append("import data to db[").append(importContext.getDbConfig().getDbUrl())
//				.append("] dbuser[").append(importContext.getDbConfig().getDbUser())
//				.append("] insert sql[").append(es2DBContext.getTargetSqlInfo() == null ?"": es2DBContext.getTargetSqlInfo().getOriginSQL()).append("] \r\nupdate sql[")
//					.append(es2DBContext.getTargetUpdateSqlInfo() == null?"":es2DBContext.getTargetUpdateSqlInfo().getOriginSQL()).append("] \r\ndelete sql[")
//					.append(es2DBContext.getTargetDeleteSqlInfo() == null ?"":es2DBContext.getTargetDeleteSqlInfo().getOriginSQL()).append("] start.");
		logger.info(taskInfo + " start.");
	}
	protected String taskInfo ;
	protected FileTransfer buildFileTransfer(FileOutputConfig fileOutputConfig, String fileName) throws IOException {
		FileTransfer fileTransfer = new FileTransfer(taskInfo, fileOutputConfig,path,fileName);

		return fileTransfer;

	}

	protected FileTransfer initFileTransfer(){
		path = fileOutputConfig.getFileDir();

		String name = fileOutputConfig.generateFileName(   taskContext, fileSeq);
		String fileName = SimpleStringUtil.getPath(path,name);
		fileSeq ++;
		try {
			String remoteFileName = !fileOutputConfig.isDisableftp()?SimpleStringUtil.getPath(fileOutputConfig.getRemoteFileDir(),name):null;
//			FileTaskContext fileTaskContext = (FileTaskContext)taskContext;
			//添加文件信息到任务监控信息中
			if(fileOutputConfig.isEnableGenFileInfoMetric()) {
				taskContext.taskExecuteMetric(new JobExecuteMetric() {
					@Override
					public void executeMetric(JobTaskMetrics jobTaskMetrics) {
						List<GenFileInfo> genFileInfos = (List<GenFileInfo>) jobTaskMetrics.readJobExecutorData(FileOutputConfig.JobExecutorDatas_genFileInfos);

						if (genFileInfos == null) {

							genFileInfos = new ArrayList<>();
							jobTaskMetrics.putJobExecutorData(FileOutputConfig.JobExecutorDatas_genFileInfos, genFileInfos);
						}

						GenFileInfo genFileInfo = new GenFileInfo();
						genFileInfo.setLocalFile(fileName);
						genFileInfo.setRemoteFile(remoteFileName);
						genFileInfos.add(genFileInfo);
					}
				});
			}



			FileTransfer fileTransfer = buildFileTransfer(fileOutputConfig,fileName);
			fileTransfer.initFtp(remoteFileName);
			fileTransfer.initTransfer();
			if(!fileOutputConfig.isDisableftp() && fileOutputConfig.getFtpOutConfig() != null) {
				StringBuilder builder = new StringBuilder().append("Import data to ftp ip[").append(fileOutputConfig.getFtpIP())
						.append("] ftp user[").append(fileOutputConfig.getFtpUser())
						.append("] ftp password[******] ftp port[")
						.append(fileOutputConfig.getFtpPort()).append("] file [")
						.append(fileName).append("]")
						.append(" remoteFileName[").append(remoteFileName).append("]");
				taskInfo = builder.toString();
//				if(fileOutputConfig.getFailedFileResendInterval() > 0) {
//					if (failedResend == null) {
//						synchronized (FailedResend.class) {
//							if (failedResend == null) {
//								failedResend = new FailedResend(fileOutputConfig);
//								failedResend.start();
//							}
//						}
//					}
//				}
//				if(fileOutputConfig.getSuccessFilesCleanInterval() > 0){
//					if(successFilesClean == null){
//						synchronized (SuccessFilesClean.class) {
//							if (successFilesClean == null) {
//								successFilesClean = new SuccessFilesClean(fileOutputConfig);
//								successFilesClean.start();
//							}
//						}
//					}
//				}
			}
			else{
				StringBuilder builder = new StringBuilder().append("Import data to file [")
						.append(fileName).append("]");
				taskInfo = builder.toString();
			}

			fileTransfer.writeHeader();
			return fileTransfer;
		} catch (Exception e) {
			throw new DataImportException("init file writer failed:"+fileName,e);
		}

	}
	@Override
	public void stop(){
		if(asynTranResultSet != null) {
			asynTranResultSet.stop();
			asynTranResultSet = null;
		}

		super.stop();
	}
	/**
	 * 只停止转换作业
	 */
	@Override
	public void stopTranOnly(){
		if(asynTranResultSet != null) {
			asynTranResultSet.stopTranOnly();
			asynTranResultSet = null;
		}
		super.stopTranOnly();
	}


	@Override
	protected void initTranJob(){
		tranJob = new StringTranJob();
	}
	@Override
	protected void initTranTaskCommand(){
		parrelTranCommand = new BaseParrelTranCommand(){

			@Override
			public int hanBatchActionTask(ImportCount totalCount, long dataSize, int taskNo, Object lastValue, Object datas, boolean reachEOFClosed,
										  CommonRecord record,ExecutorService service, List<Future> tasks, TranErrorWrapper tranErrorWrapper) {
				if(datas != null) {
					taskNo++;
					FileFtpTaskCommandImpl taskCommand = new FileFtpTaskCommandImpl(totalCount, importContext,
							dataSize, taskNo, totalCount.getJobNo(), fileTransfer, lastValue, currentStatus, reachEOFClosed, taskContext);
					taskCommand.setDatas((String) datas);
					tasks.add(service.submit(new TaskCall(taskCommand, tranErrorWrapper)));

				}
				return taskNo;
			}

			@Override
			public CommonRecord buildStringRecord(Context context, Writer writer) throws Exception {
				return FileFtpOutPutDataTran.this.buildStringRecord(context,writer);
			}

			@Override
			public void parrelCompleteAction() {
//				fileTransfer.sendFile();//传输文件
				sendFile(false);
			}
		};
		serialTranCommand = new BaseSerialTranCommand() {
			@Override
			public int hanBatchActionTask(ImportCount totalCount, long dataSize, int taskNo, Object lastValue, Object datas, boolean reachEOFClosed, CommonRecord record) {
				if(datas != null) {
					taskNo++;
					FileFtpTaskCommandImpl taskCommand = new FileFtpTaskCommandImpl(totalCount, importContext,
							dataSize, taskNo, totalCount.getJobNo(), fileTransfer, lastValue, currentStatus, reachEOFClosed, taskContext);
					taskCommand.setDatas((String) datas);
					TaskCall.call(taskCommand);

				}
				return taskNo;
			}

			@Override
			public int endSerialActionTask(ImportCount totalCount, long dataSize, int taskNo, Object lastValue, Object datas, boolean reachEOFClosed, CommonRecord record) {
				if(datas != null) {
					taskNo ++;
					FileFtpTaskCommandImpl taskCommand = new FileFtpTaskCommandImpl(totalCount, importContext,
							dataSize, taskNo, totalCount.getJobNo(), fileTransfer,lastValue,  currentStatus,reachEOFClosed,taskContext);

					taskCommand.setDatas((String)datas);
					TaskCall.call(taskCommand);
				}
				sendFile(false);
				return taskNo;
			}

			@Override
			public int splitSerialActionTask(ImportCount totalCount, long dataSize, int taskNo, Object lastValue, Object datas, boolean reachEOFClosed, CommonRecord record) {
				if(datas != null) {
					taskNo++;
					FileFtpTaskCommandImpl taskCommand = new FileFtpTaskCommandImpl(totalCount, importContext,
							dataSize, taskNo, totalCount.getJobNo(), fileTransfer, lastValue, currentStatus, reachEOFClosed, taskContext);
					taskCommand.setDatas((String)datas);
					TaskCall.call(taskCommand);

				}
				sendFile(true);
				return taskNo;
			}

			@Override
			public boolean splitCheck(long totalCount) {
				return fileOutputConfig.getMaxFileRecordSize() > 0 && totalCount > 0
						&& (totalCount % fileOutputConfig.getMaxFileRecordSize() == 0);
			}

			@Override
			public CommonRecord buildStringRecord(Context context, Writer writer) throws Exception {
				return FileFtpOutPutDataTran.this.buildStringRecord(context,writer);
			}
		};
	}
	public void init(){
		super.init();


		fileTransfer = initFileTransfer();


	}

	@Override
	public String tran() throws DataImportException {
		try {
			String ret = super.tran();
			fileTransfer.close();
			fileTransfer = null;
			return ret;
		}
		catch (DataImportException dataImportException){
			if(this.countDownLatch != null)
				countDownLatch.attachException(dataImportException);
			throw dataImportException;
		}
		catch (Exception dataImportException){
			if(this.countDownLatch != null)
				countDownLatch.attachException(dataImportException);
			throw new DataImportException(dataImportException);
		}
		catch (Throwable dataImportException){
			if(this.countDownLatch != null)
				countDownLatch.attachException(dataImportException);
			throw new DataImportException(dataImportException);
		}
		finally {
			if(this.countDownLatch != null)
				countDownLatch.countDown();
		}
	}
	public FileFtpOutPutDataTran(TaskContext taskContext, TranResultSet jdbcResultSet, ImportContext importContext,Status currentStatus) {
		super(taskContext,jdbcResultSet,importContext,   currentStatus);
		fileOutputConfig = (FileOutputConfig) importContext.getOutputConfig();
	}


	public FileFtpOutPutDataTran(TaskContext taskContext, TranResultSet jdbcResultSet, ImportContext importContext,  JobCountDownLatch countDownLatch,Status currentStatus) {
		super(taskContext,jdbcResultSet,importContext,   currentStatus);
		this.countDownLatch = countDownLatch;
		fileOutputConfig = (FileOutputConfig) importContext.getOutputConfig();
	}

	/**
	 * 并行批处理导入，ftp上传，不支持并行生成文件

	 * @return
	 */
	@Override
	public String parallelBatchExecute( ) {
		if (!fileOutputConfig.isDisableftp()) {
			return batchExecute();
		}
		return super.parallelBatchExecute() ;
	}



	protected CommonRecord buildStringRecord(Context context, Writer writer) throws Exception {
		CommonRecord record = buildRecord(  context );

		fileOutputConfig.generateReocord(context,record, writer);
//		writer.write(TranUtil.lineSeparator);
		writer.write(fileOutputConfig.getLineSeparator());
		return record;
	}


	protected void sendFile(boolean resetFileTransfer){
		if(!fileTransfer.isSended()){
			fileTransfer.sendFile();
		}
		if(resetFileTransfer)
			fileTransfer = this.initFileTransfer();
	}






}
