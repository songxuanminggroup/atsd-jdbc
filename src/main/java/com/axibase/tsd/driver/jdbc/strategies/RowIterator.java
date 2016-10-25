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
package com.axibase.tsd.driver.jdbc.strategies;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import com.axibase.tsd.driver.jdbc.content.UnivocityParserRowContext;
import com.axibase.tsd.driver.jdbc.enums.AtsdType;
import com.axibase.tsd.driver.jdbc.ext.AtsdRuntimeException;
import com.axibase.tsd.driver.jdbc.intf.ParserRowContext;
import com.axibase.tsd.driver.jdbc.util.EnumUtil;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.calcite.avatica.ColumnMetaData;

public class RowIterator implements Iterator<Object[]>, AutoCloseable {
	private static final char COMMENT_SIGN = '#';
	private static final CsvParserSettings DEFAULT_PARSER_SETTINGS;
	static {
		DEFAULT_PARSER_SETTINGS = new CsvParserSettings();
		DEFAULT_PARSER_SETTINGS.setInputBufferSize(16 * 1024);
		DEFAULT_PARSER_SETTINGS.setReadInputOnSeparateThread(false);
		DEFAULT_PARSER_SETTINGS.setCommentCollectionEnabled(false);
		DEFAULT_PARSER_SETTINGS.setEmptyValue("");
		DEFAULT_PARSER_SETTINGS.setNumberOfRowsToSkip(1);
	}


	private CsvParser decoratedParser;
	private final Reader decoratedReader;
	private ParserRowContext rowContext;
	private String commentSection;
	private Object[] nextRow;
	private String[] header;
	private AtsdType[] columnTypes;
	private boolean[] nullable;
	private int row = 0;

	private RowIterator(Reader reader, List<ColumnMetaData> columnMetadata, CsvParserSettings settings) {
		this.decoratedReader = reader;
		try {
			final int firstSymbol = reader.read();
			if (firstSymbol == COMMENT_SIGN) {
				fillCommentSectionWithReaderContent(reader);
			} else if (firstSymbol != -1) {
				fillFromMetadata(columnMetadata);
				this.nextRow = this.header;

				CsvParser parser = new CsvParser(settings);
				this.decoratedParser = parser;
				parser.beginParsing(reader);
				this.rowContext = new UnivocityParserRowContext(parser.getContext(), this.header.length);
				next();
			}
		} catch (IOException e) {
			throw new AtsdRuntimeException(e);
		}
	}

	public static RowIterator newDefaultIterator(InputStream inputStream, List<ColumnMetaData> metadata) {
		Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
		return newDefaultIterator(reader, metadata);
	}

	public static RowIterator newDefaultIterator(Reader reader, List<ColumnMetaData> metadata) {
		return new RowIterator(reader, metadata, DEFAULT_PARSER_SETTINGS);
	}

	private static CsvParserSettings prepareParserSettings(String[] header) {
		CsvParserSettings settings = new CsvParserSettings();
		settings.setInputBufferSize(16 * 1024);
		settings.setReadInputOnSeparateThread(false);
		settings.setCommentCollectionEnabled(false);
		settings.setEmptyValue("");
		settings.setHeaders(header);
		settings.selectFields(header);
		return settings;
	}

	private void fillFromMetadata(List<ColumnMetaData> columnMetadata) {
		final int length = columnMetadata.size();
		this.header = new String[length];
		this.columnTypes = new AtsdType[length];
		this.nullable = new boolean[length];
		int i = 0;
		for (ColumnMetaData metaData : columnMetadata) {
			this.header[i] = metaData.columnName;
			this.columnTypes[i] = EnumUtil.getAtsdTypeBySqlType(metaData.type.id);
			this.nullable[i] = metaData.nullable == 1;
			++i;
		}
	}

	private void fillCommentSectionWithReaderContent(Reader reader) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(reader);
		StringBuilder buffer = new StringBuilder();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			buffer.append(line.charAt(0) == COMMENT_SIGN ? line.substring(1) : line);
		}
		this.commentSection = buffer.toString();
	}

	private void fillCommentSectionWithParsedComments() {
		final String comments = decoratedParser.getContext().currentParsedContent();
		if (comments == null) {
			return;
		}
		int startIndex = comments.indexOf(COMMENT_SIGN) + 1;
		if (startIndex > 0) {
			final int length = comments.length();
			StringBuilder buffer = new StringBuilder(length - startIndex);
			while (startIndex != -1) {
				final int endIndex = comments.indexOf("\n#", startIndex);
				final String substring;
				if (endIndex == -1) {
					substring = comments.substring(startIndex);
					startIndex = -1;
				} else {
					substring = comments.substring(startIndex, endIndex);
					startIndex = endIndex + 2;
				}
				buffer.append(substring);
			}
			commentSection = buffer.toString();
		}
	}

	@Override
	public boolean hasNext() {
		return nextRow != null;
	}

	@Override
	public Object[] next() {
		if (nextRow == null) {
			return null;
		}
		Object[] old = nextRow;
		final String[] data = decoratedParser.parseNext();
		if (data == null) {
			fillCommentSectionWithParsedComments();
			nextRow = null;
		} else {
			nextRow = parseValues(data);
			++row;
		}
		return old;
	}

	private Object[] parseValues(String[] values) {
		final int length = columnTypes.length;
		if (columnTypes.length != values.length) {
			throw new AtsdRuntimeException("Parsed number of columns doesn't match to header on row=" + row);
		}
		Object[] parsed = new Object[length];
		for (int i = 0; i != length; ++i) {
			parsed[i] = columnTypes[i].readValue(values, i, nullable[i], this.rowContext);
		}
		return parsed;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("This iterator is read-only");
	}

	@Override
	public void close() throws IOException {
		if (this.decoratedParser != null) {
			this.decoratedParser.stopParsing();
		}
		if (this.decoratedReader != null) {
			this.decoratedReader.close();
		}
	}

	public CharSequence getCommentSection() {
		return commentSection;
	}

	public String[] getHeader() {
		return header;
	}
}
