package org.example;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApiResponseWorkbookTest {
    @Test void exportsEveryRedirectRepeatedRequestAndUnfinishedOutcomeWithTypedStatuses()throws Exception{
        RunRecord run=new RunRecord();run.status="SUCCEEDED";run.finishedAt="2026-10-16T00:00:00Z";run.stage("=1+1");
        EmailAttachmentFixtures.request(run,"r1");
        run.network.accept("requestWillBeSent",Map.of("requestId","r1","type","Fetch","redirectResponse",Map.of("url","https://fixture.invalid/api/old?secret=hidden","status",302),"request",Map.of("method","GET","url","https://fixture.invalid/api/bank")));
        run.network.accept("responseReceived",Map.of("requestId","r1","type","Fetch","hasExtraInfo",true,"response",Map.of("url","https://fixture.invalid/api/bank","status",200,"fromDiskCache",true)));
        run.network.accept("responseReceivedExtraInfo",Map.of("requestId","r1","statusCode",304));run.network.accept("loadingFinished",Map.of("requestId","r1"));
        EmailAttachmentFixtures.request(run,"r2");EmailAttachmentFixtures.complete(run,"r2",400);
        EmailAttachmentFixtures.request(run,"r3");run.network.accept("loadingFailed",Map.of("requestId","r3","errorText","net::ERR_FAILED"));
        EmailAttachmentFixtures.request(run,"=1+1");EmailAttachmentFixtures.request(run,"r5");run.network.accept("responseReceived",Map.of("requestId","r5","type","Fetch","response",Map.of("status",202)));
        run.network.freeze();byte[] bytes=ApiResponseWorkbook.create(run);
        Path fixture=Path.of("target","email-attachment-preview.xlsx");Files.write(fixture,bytes);
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            assertThat(book.getNumberOfSheets()).isEqualTo(1);var sheet=book.getSheetAt(0);int start=ApiResponseWorkbook.HEADER_ROW+1;
            assertThat(sheet.getRow(3).getCell(7).getNumericCellValue()).isEqualTo(6);
            assertThat(sheet.getRow(start).getCell(4).getNumericCellValue()).isEqualTo(302);assertThat(sheet.getRow(start).getCell(6).getStringCellValue()).isEqualTo("REDIRECTED");
            assertThat(sheet.getRow(start+1).getCell(4).getNumericCellValue()).isEqualTo(200);assertThat(sheet.getRow(start+1).getCell(5).getNumericCellValue()).isEqualTo(304);assertThat(sheet.getRow(start+1).getCell(9).getBooleanCellValue()).isTrue();
            assertThat(sheet.getRow(start+2).getCell(4).getNumericCellValue()).isEqualTo(400);
            assertThat(sheet.getRow(start+3).getCell(4).getCellType()).isEqualTo(CellType.BLANK);assertThat(sheet.getRow(start+3).getCell(6).getStringCellValue()).isEqualTo("FAILED");
            assertThat(sheet.getRow(start+4).getCell(0).getStringCellValue()).isEqualTo("=1+1");assertThat(sheet.getRow(start+4).getCell(6).getStringCellValue()).isEqualTo("UNFINISHED");
            assertThat(sheet.getRow(start+5).getCell(4).getNumericCellValue()).isEqualTo(202);assertThat(sheet.getRow(start+5).getCell(6).getStringCellValue()).isEqualTo("UNFINISHED");
            for(int i=start;i<start+6;i++){assertThat(sheet.getRow(i).getCell(3).getStringCellValue()).doesNotContain("?");assertThat(sheet.getRow(i).getCell(7).getCellType()).isEqualTo(CellType.STRING);assertThat(DateUtil.isCellDateFormatted(sheet.getRow(i).getCell(11))).isTrue();}
            assertThat(sheet.getPaneInformation().getVerticalSplitPosition()).isEqualTo((short)2);assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short)7);
            assertThat(sheet.getRow(start).getCell(7).getStringCellValue()).isEqualTo("=1+1");
        }
    }
    @Test void emptyReportAndMissingOrOversizedScreenshotsAreExplicit()throws Exception{
        RunRecord run=new RunRecord();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(ApiResponseWorkbook.create(run)))){assertThat(book.getSheetAt(0).getRow(7).getCell(0).getStringCellValue()).contains("No Fetch/XHR requests");}
        assertThat(RunEmailAttachments.prepare(run,RunEmailAttachments.DEFAULT_LIMIT).notes()).anyMatch(note->note.contains("No screenshots"));
        EmailAttachmentFixtures.screenshot(run,"result-page");assertThat(RunEmailAttachments.prepare(run,RunEmailAttachments.DEFAULT_LIMIT).attachments()).hasSize(2);
        assertThat(RunEmailAttachments.prepare(run,1).notes()).anyMatch(note->note.contains("size limit"));
        Files.delete(RunScreenshots.path(run.id,"result-page"));assertThat(RunEmailAttachments.prepare(run,RunEmailAttachments.DEFAULT_LIMIT).notes()).anyMatch(note->note.contains("unavailable"));
    }
}
