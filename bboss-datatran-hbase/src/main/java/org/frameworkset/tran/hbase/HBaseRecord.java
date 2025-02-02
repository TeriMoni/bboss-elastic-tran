package org.frameworkset.tran.hbase;
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


import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.frameworkset.tran.DataImportException;
import org.frameworkset.tran.record.BaseRecord;
import org.frameworkset.tran.schedule.TaskContext;
import org.frameworkset.tran.util.TranUtil;

import java.util.Date;
import java.util.Map;

/**
 * <p>Description: </p>
 * <p></p>
 * <p>Copyright (c) 2018</p>
 * @Date 2019/11/19 11:24
 * @author biaoping.yin
 * @version 1.0
 */
public class HBaseRecord extends BaseRecord{
	private Result data;
	private Map<String,byte[][]> familys;
	public HBaseRecord(TaskContext taskContext,Map<String, byte[][]> familys, Result data){
		super(taskContext);
		this.familys = familys;
		this.data = data;
	}
	@Override
	public boolean removed() {
		return false;
	}
	@Override
	public boolean reachEOFClosed(){
		return false;
	}


	private byte[][] parser(String colName){
		byte[][] cs = familys.get(colName);
		if(cs != null){
			return cs;
		}

		cs = parserColumn( colName);
		familys.put(colName,cs);
		return cs;
	}
	public static byte[][] parserColumn(String colName){
		try {
			String[] infos = colName.split(":");
			byte[] f = Bytes.toBytes(infos[0]);
			byte[] c = Bytes.toBytes(infos[1]);
			byte[][] cs = new byte[][]{f, c};
			return cs;
		}
		catch (Exception e){
			throw new DataImportException("Parser Column failed: ["+colName+"] is not a hbase colname like c:name",e);
		}
	}
	@Override
	public Object getValue(String colName) {
		byte[][] cs = parser( colName);
		return data.getValue(cs[0],cs[1]);

	}

	@Override
	public Object getValue(int i, String colName, int sqlType) throws DataImportException {
		return getValue(colName);
	}

	@Override
	public Object getValue(String colName, int sqlType) throws DataImportException {
		return getValue(colName);
	}
	@Override
	public Date getDateTimeValue(String colName) throws DataImportException {
		Object value = getValue(  colName);
		if(value == null)
			return null;
		Long time = Bytes.toLong((byte[])value);
		return TranUtil.getDateTimeValue(colName,time,taskContext.getImportContext());

	}
	@Override
	public Date getDateTimeValue(String colName,String dateformat) throws DataImportException {
		Object value = getValue(  colName);
		if(value == null)
			return null;
		Long time = Bytes.toLong((byte[])value);
		return TranUtil.getDateTimeValue(colName,time,taskContext.getImportContext());

	}
	public Object getMetaValue(String colName) {
		/**文档_id*/
//		private String id;

		if(colName.equals("rowkey"))
			return  data.getRow();
		else if(colName.equals("timestamp")){

			return new Date(data.rawCells()[0].getTimestamp());
		}

		throw new DataImportException("Get Meta Value failed: " + colName + " is not a elasticsearch document meta field.");
	}

	@Override
	public long getOffset() {
		return 0;
	}

	@Override
	public Object getKeys() {
		return null;
	}
	public Object getData(){
		return data;
	}
	public static void main(String[] args){
		String c = "c:d";
		String[] cs = c.split(":");
		System.out.println(cs[0]+":"+cs[1]);
	}

}
