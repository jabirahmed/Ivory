/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ivory.cleanup;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.ivory.IvoryException;
import org.apache.ivory.entity.AbstractTestBase;
import org.apache.ivory.entity.store.ConfigurationStore;
import org.apache.ivory.entity.v0.EntityType;
import org.apache.ivory.entity.v0.Frequency;
import org.apache.ivory.entity.v0.process.Process;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LogCleanupServiceTest extends AbstractTestBase {

	private FileSystem fs;
	private FileSystem tfs;
	private MiniDFSCluster targetDfsCluster;
	Path instanceLogPath = new Path(
			"/projects/ivory/staging/ivory/workflows/process/" + "sample"
					+ "/logs/job-2010-01-01-01-00/000");
	Path instanceLogPath1 = new Path(
			"/projects/ivory/staging/ivory/workflows/process/" + "sample"
					+ "/logs/job-2010-01-01-01-00/001");
	Path instanceLogPath2 = new Path(
			"/projects/ivory/staging/ivory/workflows/process/" + "sample"
					+ "/logs/job-2010-01-01-02-00/001");
	Path instanceLogPath3 = new Path(
			"/projects/ivory/staging/ivory/workflows/process/" + "sample2"
					+ "/logs/job-2010-01-01-01-00/000");
	Path instanceLogPath4 = new Path(
			"/projects/ivory/staging/ivory/workflows/process/" + "sample"
					+ "/logs/latedata/2010-01-01-01-00");
	Path feedInstanceLogPath = new Path(
			"/projects/ivory/staging/ivory/workflows/feed/"
					+ "impressionFeed"
					+ "/logs/job-2010-01-01-01-00/testCluster/000");
	Path feedInstanceLogPath1 = new Path(
			"/projects/ivory/staging/ivory/workflows/feed/"
					+ "impressionFeed2"
					+ "/logs/job-2010-01-01-01-00/testCluster/000");


	@AfterClass
	public void tearDown() {
		this.dfsCluster.shutdown();
		this.targetDfsCluster.shutdown();
	}

	@BeforeClass
	public void setup() throws Exception {
		conf.set("hadoop.log.dir", "/tmp");
		this.dfsCluster = new MiniDFSCluster(conf, 1, true, null);
		fs = dfsCluster.getFileSystem();
		
		storeEntity(EntityType.CLUSTER, "testCluster");
		conf = new Configuration();
		System.setProperty("test.build.data",
				"target/tdfs/data" + System.currentTimeMillis());
		this.targetDfsCluster = new MiniDFSCluster(conf, 1, true, null);
		storeEntity(EntityType.CLUSTER, "backupCluster");
		storeEntity(EntityType.FEED, "impressionFeed");
		storeEntity(EntityType.FEED, "clicksFeed");
		storeEntity(EntityType.FEED, "imp-click-join1");
		storeEntity(EntityType.FEED, "imp-click-join2");
		storeEntity(EntityType.PROCESS, "sample");
		Process process = ConfigurationStore.get().get(EntityType.PROCESS,
				"sample");
		Process otherProcess = (Process) process.clone();
		otherProcess.setName("sample2");
		otherProcess.setFrequency(new Frequency("days(1)"));
		ConfigurationStore.get().remove(EntityType.PROCESS,
				otherProcess.getName());
		ConfigurationStore.get().publish(EntityType.PROCESS, otherProcess);		

		fs.mkdirs(instanceLogPath);
		fs.mkdirs(instanceLogPath1);
		fs.mkdirs(instanceLogPath2);
		fs.mkdirs(instanceLogPath3);
		fs.mkdirs(instanceLogPath4);

		// fs.setTimes wont work on dirs
		fs.createNewFile(new Path(instanceLogPath, "oozie.log"));
		fs.createNewFile(new Path(instanceLogPath, "pigAction_SUCCEEDED.log"));
		
		tfs = targetDfsCluster.getFileSystem();
		fs.mkdirs(feedInstanceLogPath);
		fs.mkdirs(feedInstanceLogPath1);
		tfs.mkdirs(feedInstanceLogPath);
		tfs.mkdirs(feedInstanceLogPath1);
		fs.createNewFile(new Path(feedInstanceLogPath, "oozie.log"));
		tfs.createNewFile(new Path(feedInstanceLogPath, "oozie.log"));
		
		Thread.sleep(61000);


	}

	@Test
	public void testProcessLogs() throws IOException, IvoryException,
			InterruptedException {

		AbstractCleanupHandler processCleanupHandler = new ProcessCleanupHandler();
		processCleanupHandler.cleanup();

		Assert.assertFalse(fs.exists(instanceLogPath));
		Assert.assertFalse(fs.exists(instanceLogPath1));
		Assert.assertFalse(fs.exists(instanceLogPath2));
		Assert.assertTrue(fs.exists(instanceLogPath3));

	}

	@Test
	public void testFeedLogs() throws IOException, IvoryException,
			InterruptedException {

		AbstractCleanupHandler feedCleanupHandler = new FeedCleanupHandler();
		feedCleanupHandler.cleanup();

		Assert.assertFalse(fs.exists(feedInstanceLogPath));
		Assert.assertFalse(tfs.exists(feedInstanceLogPath));
		Assert.assertTrue(fs.exists(feedInstanceLogPath1));
		Assert.assertTrue(tfs.exists(feedInstanceLogPath1));

	}
}
