package com.sachin.pdf;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Sachin
 * @Date 2021/3/13
 **/
public class TestPDF {

    public static Map<String, PDFFile> cache = new HashMap();
    public static Set<String> modifyCategoryCache = new HashSet<>();//PDF文件被修改过的 category
    public static Set<String> allCategory = new HashSet<>();

    static Pattern pattern = Pattern.compile("^第.*章");
    public static volatile int count = 0;

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            Boolean forceToGenAll = Boolean.valueOf(args[0]);
            if (forceToGenAll) {
                //删除序列化文件
                createNewFile("G:\\programme\\书签序列化对象.txt");
            }
        }
        Long time = System.currentTimeMillis();
        deserializePDFFiles("G:\\programme\\书签序列化对象.txt");
        File file = new File("G:\\programme");
        File bookrmarkFile = new File("G:\\programme\\书籍书签.txt");
        genPDFbookmark(file);
        seriableizeToFile("G:\\programme\\书签序列化对象.txt");
        // writeToCategoryBookmarkFile(time, file);
        writeAllToBookrmarkFile(bookrmarkFile);

    }

    private static void writeAllToBookrmarkFile(File bookrmarkFile) {
        System.out.println("===================>");
        if (bookrmarkFile.exists()) {
            bookrmarkFile.delete();
        }
        bookrmarkFile.getParentFile().mkdirs();
        allCategory.forEach(category -> {

            StringBuilder builder = new StringBuilder();
            String fileDirectory = builder.append("G:\\programme").append("\\").append(category).append("\\").append("书签").toString();
            File fileDir = new File(fileDirectory);
            if (fileDir.exists() && fileDir.isDirectory()) {
                File[] files = fileDir.listFiles();
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".txt") && !file.getName().contains("总书签") && !file.getName().contains(SpellHelper.getEname("总书签")) && !file.getName().contains("zongshuqian")) {
                        System.out.println("文件" + file.getAbsolutePath() + "写入到总文件" + bookrmarkFile.getAbsolutePath());
                        appendFromFileToFile(file.getAbsolutePath(), bookrmarkFile.getAbsolutePath());
                    } else {
                        if (file.isFile()) {
                            //  System.out.println("总书签文件不需要写入");
                        }
                    }
                }

            }
        });

    }

    private static void writeToCategoryBookmarkFile(Long time, File file) {
        cache.values().forEach(pdfFile -> {

            if (!modifyCategoryCache.contains(pdfFile.getFileCategory())) {
                return;
            }
            File categoryBookmarkFile = new File(pdfFile.getCategoryBookmarkFilePath());

            if (file.exists() && categoryBookmarkFile.lastModified() < time) {
                file.delete();
            }
            appendFromFileToFile(pdfFile.getBookmarkFilePath(), pdfFile.getCategoryBookmarkFilePath());
        });
    }

    private static void appendFromFileToFile(String sourceFile, String desFile) {
        //createNewFile:如果D:/test 目录下没有 1.txt文件，则创建该文件；如果没有test目录，直接抛出异常，如果1.txt已经存在，
        // 那么文件创建失败。 可以得知，createNewFile() 方法，根据抽象路径创建一个新的空文件，当抽象路径制定的文件存在时，创建失败。
        File srcFile = new File(sourceFile);
        if (!srcFile.exists()) {
            return;
        }
        File newCategoryBookmarkFile = new File(desFile);
        newCategoryBookmarkFile.getParentFile().mkdirs();
        BufferedReader br = null;
        PrintWriter pw = null;
        try {
            br = new BufferedReader(new FileReader(srcFile));
            pw = new PrintWriter(new FileWriter(newCategoryBookmarkFile, true));
            String line = br.readLine();
            int tag = 0;
            while ((line = br.readLine()) != null) {
                if (tag == 0) {
                    pw.println("");
                }
                tag++;
                pw.println(line);
            }
            br.close();
            pw.close();
            FileUtils.writeStringToFile(new File(desFile), "\n==================================================分割线===================================================================\n", "UTF-8", true);
            // srcFile.delete();//删除文件
        } catch (Exception e) {

            try {

                if (br != null) {
                    br.close();
                }
            } catch (Exception e1) {


            }
            if (pw != null) {
                pw.close();
            }
            System.out.println("文件书签：" + sourceFile + "写入到总书签：" + desFile + "失败");
            e.printStackTrace();
        }
    }

    private static void genPDFbookmark(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();

            Arrays.stream(files).forEach(fileItem -> {
                genPDFbookmark(fileItem);

            });
        } else {
            extraPdfBookmark(file);
        }

    }

    public static void extraPdfBookmark(File file) {


        //判断文件是否是PDF文件
        PDDocument document = null;
        String filePath = file.getPath();//D:\临时文件\0126\测试.pdf
        String fileName = file.getName();//测试.pdf
        if (!fileName.endsWith(".pdf")) {
            return;
        }
        String fileNameWittoutExtend = fileName.substring(0, fileName.length() - 4);
        try {
            //Java中反斜杠表示转义，因此要用两个反斜杠表示一个反斜杠。
            String[] split = filePath.split("\\\\");//D:\临时文件\0126\测试.pdf
            if (split.length < 4) {
                return;
            }

            String fileCategory = split[2];
            allCategory.add(fileCategory);
            long lastModified = file.lastModified();
            PDFFile dbPdfFile = cache.get(filePath);

            if (dbPdfFile != null && dbPdfFile.getLastModifyTime() >= lastModified) {
                return;
            }
            System.out.println(filePath + "修改过，需要重新生成书签");
            modifyCategoryCache.add(fileCategory);
            StringBuilder bookmarkFileBuilder = new StringBuilder();
            bookmarkFileBuilder.append("G:\\programme").append("\\").append(fileCategory).append("\\").append("书签").append("\\").append(SpellHelper.getEname(fileNameWittoutExtend)).append(".txt");
            StringBuilder categoryBookmarkFileBuilder = new StringBuilder();
            categoryBookmarkFileBuilder.append("G:\\programme").append("\\").append(fileCategory).append("\\").append("书签").append("\\").append(SpellHelper.getEname("a" + fileCategory + "总书签")).append(".txt");
            File bookmarkFile = new File(bookmarkFileBuilder.toString());
            if (bookmarkFile.exists()) {
                bookmarkFile.delete();
            }
            PDFFile pdfFile = new PDFFile(filePath, fileName, fileCategory, lastModified, bookmarkFileBuilder.toString(), categoryBookmarkFileBuilder.toString());
            cache.put(filePath, pdfFile);
            allCategory.add(pdfFile.getFileCategory());
            document = PDDocument.load(file);
            //PDF文件的书签内容大纲
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline != null) {
                FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n书名：《" + fileName + "》", "UTF-8", true);
                Integer level = 0;    // A nesting level
                writeBookMark(document, outline, level, bookmarkFile, fileNameWittoutExtend);
                COSDocument cos = document.getDocument();
                cos.close();
                document.close();
            }

        } catch (Exception e) {
            System.out.println("提取PDF文件 书签出现异常：" + filePath);
            e.printStackTrace();
            if (document != null) {
                try {
                    COSDocument cos = document.getDocument();
                    cos.close();
                    document.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }

        }


    }

    public static void writeBookMark(PDDocument document, PDOutlineNode outline, Integer level, File bookmarkFile, String fileName) throws Exception {


        //得到第一个书签
        PDOutlineItem current = outline.getFirstChild();
        while (current != null) {
            int pageNumber = 1;   // 书签所在页面
            PDPageTree pdPageTree = document.getDocumentCatalog().getPages();
            Iterator<PDPage> iterator = pdPageTree.iterator();
            while (iterator.hasNext()) {
                PDPage page = iterator.next();
                if (page.equals(current.findDestinationPage(document))) {
                    //书签的注释
                    List<PDAnnotation> annotations = page.getAnnotations();
                    break;    // pageNumbed finded

                }
                pageNumber++;
            }
            StringBuilder line = new StringBuilder();
            for (int k = 0; k < level; k++) {
                line.append("\t");
            }
            String title = current.getTitle().trim();
            line.append(title);
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                //第十四章 《 spring实战》
                line.append("  《");
                line.append(fileName);//文件名
                line.append("》");
            }
            line.append("   ");
            line.append(pageNumber);
            if (!isNumeric(current.getTitle())) {//如果只包含数字就不写入了
                //处理当前书签的子书签
                System.out.println(line);
                FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n" + line.toString(), "UTF-8", true);
            }
            writeBookMark(document, current, level + 1, bookmarkFile, fileName);
            //处理当前书签的兄弟书签
            current = current.getNextSibling();
        }


    }

    public static boolean isNumeric(String str) {
        str = str.trim();
        for (int i = str.length(); --i >= 0; ) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    public static void deserializePDFFiles(String filePath) throws Exception {
        ObjectInputStream oi = null;
        FileInputStream fileInputStream = null;
        try {
            File derFile = new File(filePath);
            if (!derFile.exists()) {
                return;
            }
            fileInputStream = new FileInputStream(new File(filePath));
            oi = new ObjectInputStream(fileInputStream);
        } catch (Exception e) {

            e.printStackTrace();
        }
        if (oi == null) {
            return;
        }

        List<PDFFile> files = (List<PDFFile>) oi.readObject();
        files.forEach(file -> {
            cache.put(file.getFilePath(), file);
            allCategory.add(file.getFileCategory());
        });


    }

    public static void seriableizeToFile(String filePath) {

        try {
            createNewFile(filePath);
            ObjectOutputStream oo = new ObjectOutputStream(new FileOutputStream(filePath, false));
            ArrayList<PDFFile> pdfFiles = new ArrayList<>();
            cache.values().forEach(pdfFile -> {
                pdfFiles.add(pdfFile);
            });
            oo.writeObject(pdfFiles);
            oo.flush();
            oo.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void createNewFile(String filePath) {
        try {
            File file = new File(filePath);

            if (file.exists()) {
                file.delete();
            }
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
