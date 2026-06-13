package com.tkevinb.ragent.rag.ingestion;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析器 — 基于 Apache Tika
 * <p>
 * 支持格式：PDF, DOCX, PPTX, XLSX, HTML, Markdown, TXT
 */
@Service
public class DocumentParser {

    private final AutoDetectParser parser = new AutoDetectParser();

    /**
     * 解析文件字节内容为纯文本
     * @param bytes  文件内容
     * @param fileName 文件名（用于 Tika 识别类型）
     * @return 解析后的纯文本
     */
    public String parse(byte[] bytes, String fileName) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            Metadata metadata = new Metadata();
            metadata.set("resourceName", fileName);
            ToTextContentHandler handler = new ToTextContentHandler();
            parser.parse(is, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (SAXException | TikaException e) {
            throw new IOException("文档解析失败: " + fileName, e);
        }
    }
}
