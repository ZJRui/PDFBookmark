package com.sachin.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @Author Sachin
 * @Date 2021/3/13
 **/
public class PDFUtils {

    public PDDocument document = null;

    public String pdfPath;

    public File outFile;

    private FileWriter outFileWriter;

    /**
     * This will print the documents data.
     *
     * @param args The command line arguments.
     *
     * @throws Exception If there is an error parsing the document or io with file.
     */
    public static void main( String[] args ) throws Exception
    { PDFUtils pdfToc = new PDFUtils();
        pdfToc.execute();
    }

    protected void execute() throws IOException {


        try {
            File file = new File("D:\\临时文件\\0126\\测试.pdf");

            //System.out.println(file.exists() + " " + file.getPath() + " " + file.length());
            document = PDDocument.load( file );

            //PDF文件的书签内容大纲
            PDDocumentOutline outline =  document.getDocumentCatalog().getDocumentOutline();
            if( outline != null )
            {
                Integer level = 0;    // A nesting level
                printBookmark( outline, level );

            }


        }
        catch (IOException ex)
        {
            Logger.getLogger(PDFUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally
        {
            if( document != null )
            {
                document.close();
            }
            if (outFileWriter != null && isOutInFile()) {
                outFileWriter.flush();
                outFileWriter.close();
            }
        }

    }

    /**
     * Print help.
     */
    private static void usage()
    {
        System.err.println( "Usage: java -jar PdfToc.jar -i <input-pdf> [<output-pdf>]" );
    }

    /**
     * This will print the documents bookmarks to System.out.
     *
     * @param bookmark The bookmark to print out.
     * @param level A nesting level
     *
     * @throws IOException If there is an error getting the page count.
     */
    protected void printBookmark(PDOutlineNode bookmark, Integer level ) throws IOException

    {
        //得到第一个书签
        PDOutlineItem current = bookmark.getFirstChild();
        while( current != null )
        {
            int pageNumber = 1;   // 书签所在页面

            PDPageTree pdPageTree = document.getDocumentCatalog().getPages();
            Iterator<PDPage> iterator = pdPageTree.iterator();
            while (iterator.hasNext()) {
                PDPage page = iterator.next();
                if (page.equals(current.findDestinationPage(document))) {
                    //书签的注释
                    List<PDAnnotation> annotations = page.getAnnotations();
                    for (int k = 0; k < annotations.size(); k++) {
                        PDAnnotation pageAnnotation = annotations.get(k);

                        System.out.println(pageAnnotation.getAppearance());
                        System.out.println(pageAnnotation.getContents()
                        );
                        System.out.println(pageAnnotation.getAnnotationName()
                        );
                        System.out.println(pageAnnotation.getOptionalContent());
                        System.out.println("start---");
                        System.out.println(annotations.get(k).getSubtype());
                        System.out.println(annotations.get(k).getContents());
                        System.out.println("---end");
                    }


                    break;    // pageNumbed finded

                }
                pageNumber++;
            }

            String out = pageNumber  + "  " + current.getTitle();
            System.out.println(out);
            //处理当前书签的子书签
            printBookmark( current, level + 1 );
            //处理当前书签的兄弟书签
            current = current.getNextSibling();
        }
    }

    /**
     *
     * @return Boolean must we out in file or not
     */
    protected boolean isOutInFile() {
        return outFile != null;
    }

    /**
     * @return the outFileWriter
     */
    public FileWriter getOutFileWriter() {
        return outFileWriter;
    }

    /**
     * @param outFileWriter the outFileWriter to set
     */
    public void setOutFileWriter(FileWriter outFileWriter) {
        this.outFileWriter = outFileWriter;
    }
}
