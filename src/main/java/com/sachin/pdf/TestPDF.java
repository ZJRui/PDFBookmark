package com.sachin.pdf;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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
@SuppressWarnings("all")
public class TestPDF {

    public static Map<String, PDFFile> cache = new HashMap();
    public static Set<String> modifyCategoryCache = new HashSet<>();// PDF文件被修改过的 category
    public static Set<String> allCategory = new HashSet<>();

    static Pattern pattern = Pattern.compile("^第.*章");
    public static volatile int count = 0;
    /**
     * 扫描的目录
     */
   static String targetDir = "";

   static String bookmarkFileName="书籍书签.txt";
   static String bookmarkFilePath="";

    /**
     * 书签序列化后的文件对象
     */
   static String bookMarkSeriFileName="书签序列化对象.txt";
    /**
     * 书签序列化后的文件路径
     */
    static String bookMarkSeriFilePath = "";
    public static void main(String[] args) throws Exception {


        // if (args.length != 2) {
        //     throw new RuntimeException("请指定三个参数，参数1：要扫描的目录 ，参数2：true|false 是否阐述 第一个参数指定目录下的'书签序列化对象.txt'文件、" +
        //             "");
        // }

        args=new String[]{"/Users/dz0400847/testPDf"};
        //参数1
        String arg1 = args[0];
        targetDir=arg1;
        if(targetDir.contains("\\")){
            throw new RuntimeException("请使用/而不是\\");
        }
        if(arg1.endsWith("/")||arg1.endsWith("\\")){
           throw new RuntimeException("参数1 不需要带\\或者/结尾");
        }else{
           if(arg1.contains("/")){
               bookMarkSeriFilePath=arg1+"/"+bookMarkSeriFileName;
               bookmarkFilePath=arg1+"/"+bookmarkFileName;
           }else{
               bookMarkSeriFilePath = arg1 + "\\" + bookMarkSeriFileName;
               bookmarkFilePath=arg1+"\\"+bookmarkFileName;
           }
        }
        System.out.println("书签序列化后存储文件路径："+bookMarkSeriFilePath);
        System.out.println("书籍书签.txt路径：："+bookmarkFilePath);
        if(args.length>1){
            //参数2
            Boolean forceToGenAll = Boolean.valueOf(args[1]);
            if (forceToGenAll) {
                // 删除序列化文件
                createNewFile(bookMarkSeriFilePath);
            }
        }
        //step1 反序列化文件
        Long time = System.currentTimeMillis();
        deserializePDFFiles(bookMarkSeriFilePath);
        File file = new File(targetDir);
        File bookrmarkFile = new File(bookmarkFilePath);
        //step2生成文件
        genPDFbookmark(file,file.getAbsolutePath());
        seriableizeToFile(bookMarkSeriFilePath);
        // writeToCategoryBookmarkFile(time, file);
        writeAllToBookrmarkFile(bookrmarkFile,targetDir);

    }

    private static void writeAllToBookrmarkFile(File bookrmarkFile,String targetDir) {
        System.out.println("===================>");
        if (bookrmarkFile.exists()) {
            bookrmarkFile.delete();
        }
        bookrmarkFile.getParentFile().mkdirs();
        allCategory.forEach(category -> {
            if(StringUtils.isEmpty(category)){
                return;
            }
            StringBuilder builder = new StringBuilder();
            String fileDirectory = builder.append(targetDir).append("/").append(category).append("/").append("书签").toString();
            File fileDir = new File(fileDirectory);
            if (fileDir.exists() && fileDir.isDirectory()) {
                File[] files = fileDir.listFiles();
                for (File file : files) {
                    //分类目录下的 txt文件，这个txt文件 所在的目录必须包含 书签两个字， 且文件名不能包含总书签三个字
                    if (file.isFile() &&
                            file.getName().endsWith(".txt")
                            && !file.getName().contains("总书签")
                            && !file.getName().contains(SpellHelper.getEname("总书签"))
                            && !file.getName().contains("zongshuqian")
                            &&file.getAbsolutePath().contains("书签")) {
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
        // createNewFile:如果D:/test 目录下没有 1.txt文件，则创建该文件；如果没有test目录，直接抛出异常，如果1.txt已经存在，
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
            InputStreamReader fReader = new InputStreamReader(new FileInputStream(srcFile), "UTF-8");
            br = new BufferedReader(fReader);
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

    private static void genPDFbookmark(File file,String targetDir) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            Arrays.stream(files).forEach(fileItem -> {
                genPDFbookmark(fileItem,targetDir);
            });
        } else {
            extraPdfBookmark(file,targetDir);
        }
    }

    public static void extraPdfBookmark(File file,String targetDir) {
        // 判断文件是否是PDF文件
        PDDocument document = null;
        String filePath = file.getPath();// D:\临时文件\0126\测试.pdf
        String fileName = file.getName();// 测试.pdf
        if (!fileName.endsWith(".pdf")) {
            return;
        }
        String fileNameWithoutExtend = fileName.substring(0, fileName.length() - 4);
        try {
            //书籍的分类目录
            String fileCategory = "";
            // /docs/tmp/a/a.pdf  replace  /doc/tmp --> /a/a.pdf--->{"","a","a.pdf"}
            String[] splitTags = filePath.replaceAll(targetDir, "").split("/");
            for (String splitTag : splitTags) {
                if(splitTag!=null&&splitTag.length()>0){
                    fileCategory=splitTag;
                    break;
                }
            }

            allCategory.add(fileCategory);
            long lastModified = file.lastModified();
            PDFFile dbPdfFile = cache.get(filePath);
            if (dbPdfFile != null && dbPdfFile.getLastModifyTime() >= lastModified) {
                return;
            }
          /*  count++;
            if (count >4) {
                return;
            }*/
            System.out.println(filePath + "修改过，需要重新生成书签");
            modifyCategoryCache.add(fileCategory);
            StringBuilder bookmarkFileBuilder = new StringBuilder();
            bookmarkFileBuilder.append(targetDir).append("/").append(fileCategory).append("/").append("书签").append("/").append(fileNameWithoutExtend).append(".txt");
            StringBuilder categoryBookmarkFileBuilder = new StringBuilder();
            categoryBookmarkFileBuilder.append(targetDir).append("/").append(fileCategory).append("/").append("书签").append("/").append("001" + fileCategory + "-总书签").append(".txt");
            File bookmarkFile = new File(bookmarkFileBuilder.toString());
            if (bookmarkFile.exists()) {
                bookmarkFile.delete();
            }
            PDFFile pdfFile = new PDFFile(filePath, fileName, fileCategory, lastModified, bookmarkFileBuilder.toString(), categoryBookmarkFileBuilder.toString());
            cache.put(filePath, pdfFile);
            allCategory.add(pdfFile.getFileCategory());

            //加载PDF
            document = PDDocument.load(file);
            COSDocument cos = document.getDocument();
            // PDF文件的书签内容大纲
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n书名：《" + fileName + "》", "UTF-8", true);
            if (outline != null) {
                Integer level = 0;    // A nesting level
                writeBookMark(document, outline, level, bookmarkFile, fileNameWithoutExtend);
            }else{
                //没有大纲内容，只写入 文件名
               // FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n书名：《" + fileName + "》", "UTF-8", true);
            }
        } catch (Exception e) {
            System.out.println("提取PDF文件 书签出现异常：" + filePath);
            e.printStackTrace();
        }finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }


    }

    public static void writeBookMark(PDDocument document, PDOutlineNode outline, Integer level, File bookmarkFile, String fileName) throws Exception {

        // 得到第一个书签
        PDOutlineItem current = outline.getFirstChild();
        while (current != null) {
            int pageNumber = 1;   // 书签所在页面
            PDPageTree pdPageTree = document.getDocumentCatalog().getPages();
            Iterator<PDPage> iterator = pdPageTree.iterator();
            while (iterator.hasNext()) {
                PDPage page = iterator.next();
                if (page.equals(current.findDestinationPage(document))) {
                    // 书签的注释
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
                // 第十四章 《 spring实战》
                line.append("  《");
                line.append(fileName);// 文件名
                line.append("》");
            }
            line.append("   ");
            line.append(pageNumber);
            if (!isNumeric(current.getTitle())) {// 如果只包含数字就不写入了
                // 处理当前书签的子书签
                FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n" + line.toString(), "UTF-8", true);
            }
            writeBookMark(document, current, level + 1, bookmarkFile, fileName);
            // 处理当前书签的兄弟书签
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
