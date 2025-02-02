package org.frameworkset.tran.ftp;
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

import org.frameworkset.tran.input.file.FileConfig;
import org.frameworkset.tran.input.file.FileFilter;
import org.frameworkset.tran.input.file.FtpFileFilter;

import java.util.List;

/**
 * <p>Description: </p>
 * <p></p>
 * <p>Copyright (c) 2020</p>
 * @Date 2021/9/27 14:50
 * @author biaoping.yin
 * @version 1.0
 */
public interface FtpContext {
	String getFtpIP();
	RemoteFileValidate getRemoteFileValidate();
	int getFtpPort();
	FtpConfig getFtpConfig();
	FileConfig getFileConfig();
	String getRemoteFileDir();
	boolean deleteRemoteFile();

	public String getFtpUser() ;

	public String getFtpPassword() ;
	public List<String> getHostKeyVerifiers();

	public String getFtpProtocol();
	public String getFtpTrustmgr();
	public Boolean localActive();
	public Boolean useEpsvWithIPv4();
	public int getTransferProtocol();
	public String getFtpProxyHost();
	public int getFtpProxyPort();
	public String getFtpProxyUser();
	public String getFtpProxyPassword();
	public boolean printHash();
	public Boolean binaryTransfer();
	public long getKeepAliveTimeout();
	public int getControlKeepAliveReplyTimeout();
	FileFilter getFileFilter();
	FtpFileFilter getFtpFileFilter();

	String getEncoding();
}
