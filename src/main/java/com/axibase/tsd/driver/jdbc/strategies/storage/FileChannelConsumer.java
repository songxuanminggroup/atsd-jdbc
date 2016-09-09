/*
* Copyright 2016 Axibase Corporation or its affiliates. All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License").
* You may not use this file except in compliance with the License.
* A copy of the License is located at
*
* https://www.axibase.com/atsd/axibase-apache-2.0.pdf
*
* or in the "license" file accompanying this file. This file is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied. See the License for the specific language governing
* permissions and limitations under the License.
*/
package com.axibase.tsd.driver.jdbc.strategies.storage;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.util.Iterator;

import com.axibase.tsd.driver.jdbc.content.StatementContext;
import com.axibase.tsd.driver.jdbc.ext.AtsdException;
import com.axibase.tsd.driver.jdbc.strategies.AbstractConsumer;
import com.axibase.tsd.driver.jdbc.strategies.StrategyStatus;

public class FileChannelConsumer extends AbstractConsumer {
	public FileChannelConsumer(final StatementContext context, final StrategyStatus status) {
		super(context, status);
	}

	public Iterator<String[]> getIterator() throws AtsdException {
		return super.getIterator("File");
	}

	@Override
	public String[] open(Channel channel) throws IOException {
		AsynchronousFileChannel readChannel = (AsynchronousFileChannel) channel;
		iterator = new FileChannelIterator(readChannel, context, status);
		return iterator.next();
	}

}
