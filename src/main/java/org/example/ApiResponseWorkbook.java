package org.example;

import java.io.*;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** A literal export of the browser ledger. No API calls and no inferred response codes. */
final class ApiResponseWorkbook {
    static final int HEADER_ROW=6;
    static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String[] HEADERS={"Request ID","Redirect hop","Method","Endpoint","HTTP status","Wire status","Lifecycle","Workflow step","Resource type","Cached","Service worker","Started at UTC","Finished at UTC","Error"};
    static byte[] create(RunRecord run)throws IOException{
        List<NetworkLedger.Entry> entries=run.network.snapshot();
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            Sheet sheet=book.createSheet("API responses");sheet.setDisplayGridlines(false);
            Font bodyFont=book.createFont();bodyFont.setFontName("Arial");bodyFont.setFontHeightInPoints((short)10);
            CellStyle body=book.createCellStyle();body.setFont(bodyFont);body.setVerticalAlignment(VerticalAlignment.CENTER);
            Font titleFont=book.createFont();titleFont.setFontName("Arial");titleFont.setFontHeightInPoints((short)14);titleFont.setBold(true);
            CellStyle title=book.createCellStyle();title.setFont(titleFont);
            Font headerFont=book.createFont();headerFont.setFontName("Arial");headerFont.setFontHeightInPoints((short)10);headerFont.setBold(true);headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle header=book.createCellStyle();header.setFont(headerFont);header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);header.setAlignment(HorizontalAlignment.CENTER);header.setVerticalAlignment(VerticalAlignment.CENTER);header.setWrapText(true);
            CellStyle date=book.createCellStyle();date.cloneStyleFrom(body);date.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss.000"));
            CellStyle wrapped=book.createCellStyle();wrapped.cloneStyleFrom(body);wrapped.setWrapText(true);
            cell(sheet.createRow(1),0,"API response report",title);
            Row info=sheet.createRow(2);cell(info,0,"Run ID",body);cell(info,3,run.id,body);cell(info,4,"Run type",body);cell(info,5,run.kind.name(),body);cell(info,6,"Run status",body);cell(info,7,run.status,body);
            Row timing=sheet.createRow(3);cell(timing,0,"Started at UTC",body);dateCell(timing,3,run.startedAt,date,body);cell(timing,9,"Finished at UTC",body);dateCell(timing,11,run.finishedAt,date,body);cell(timing,6,"Request hops",body);cell(timing,7,entries.size(),body);
            cell(sheet.createRow(4),0,"Source: browser Fetch/XHR lifecycle events. Blank status means no HTTP response was observed. URLs omit query strings; headers and bodies are excluded.",body);
            Row headers=sheet.createRow(HEADER_ROW);headers.setHeightInPoints(30);for(int i=0;i<HEADERS.length;i++)cell(headers,i,HEADERS[i],header);
            int index=HEADER_ROW+1;
            for(NetworkLedger.Entry e:entries){
                Row row=sheet.createRow(index++);row.setHeightInPoints(32);
                Object[] values={e.requestId(),e.hop(),e.method(),e.url(),e.status(),e.wireStatus(),e.state(),e.step(),e.resourceType(),e.cached(),e.serviceWorker()};
                for(int i=0;i<values.length;i++)cell(row,i,values[i],i==3||i==7?wrapped:body);
                dateCell(row,11,e.startedAt(),date,body);dateCell(row,12,e.finishedAt(),date,body);cell(row,13,e.error(),wrapped);
            }
            if(entries.isEmpty())cell(sheet.createRow(index),0,"No Fetch/XHR requests were observed for this run.",body);
            int[] widths={24,14,12,68,14,14,21,54,16,12,16,26,26,60};
            for(int i=0;i<widths.length;i++)sheet.setColumnWidth(i,widths[i]*256);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW,Math.max(HEADER_ROW,index-1),0,HEADERS.length-1));sheet.createFreezePane(2,HEADER_ROW+1);
            sheet.setRepeatingRows(new CellRangeAddress(HEADER_ROW,HEADER_ROW,-1,-1));sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);
            book.write(bytes);return bytes.toByteArray();
        }
    }
    private static void cell(Row row,int column,Object value,CellStyle style){
        Cell cell=row.createCell(column);cell.setCellStyle(style);
        if(value instanceof Number n)cell.setCellValue(n.doubleValue());else if(value instanceof Boolean b)cell.setCellValue(b);
        else if(value!=null)cell.setCellValue(value.toString()); // Always literal strings, never formulas from captured values.
    }
    private static void dateCell(Row row,int column,String value,CellStyle date,CellStyle body){
        if(value==null){cell(row,column,null,body);return;}
        try{Cell cell=row.createCell(column);cell.setCellStyle(date);cell.setCellValue(LocalDateTime.ofInstant(Instant.parse(value),ZoneOffset.UTC));}
        catch(java.time.format.DateTimeParseException e){cell(row,column,value,body);}
    }
}
