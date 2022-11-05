package com.sachin.pdf;

import lombok.AllArgsConstructor;
import lombok.Data;
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
    @AllArgsConstructor
    @Data
    static class CategoryInfo {
        // 分类的id，我们使用分类 书签目录表示：  E:\programme\python\书签
        String categoryId = "";
        // 分类书签目录   E:\programme\python\书签
        String categoryBookmarkDir = "";

        // 分类书签路径 E:\programme\python\书签\001-总书签.txt
        String categoryBookmarkFilePath = "";

        boolean needReGenCategoryBookmark = false;

    }

    /**
     * 存储所有PDF文件的书签路径， 根据这个set，我们可以确定 书签目录下的一个书签是否是有效的书签，来确定是否删除一些无效的书签
     */
    public static Set<String> allPDFBookMarkSet = new HashSet<>();
    public static Map<String, PDFFile> cache = new HashMap();
    public static Map<String, CategoryInfo> categoryInfoMap = new HashMap<>();

    static Pattern pattern = Pattern.compile("^第.*章");
    public static volatile int count = 0;
    /**
     * 扫描的目录
     */
    static String targetDir = "";

    static String bookmarkFileName = "书籍书签.txt";
    static String bookmarkFilePath = "";

    /**
     * 书签序列化后的文件对象
     */
    static String bookMarkSeriFileName = "书签序列化对象.txt";
    /**
     * 书签序列化后的文件路径
     */
    static String bookMarkSeriFilePath = "";

    static String separator = "";
    //正则表示
    static String regSeparator = "";

    public static void main(String[] args) throws Exception {


        // if (args.length != 2) {
        //     throw new RuntimeException("请指定三个参数，参数1：要扫描的目录 ，参数2：true|false 是否阐述 第一个参数指定目录下的'书签序列化对象.txt'文件、" +
        //             "");
        // }

        args = new String[]{"D:\\tempFiles\\pdftest"};

        // 参数1
        String arg1 = args[0];
        init(arg1);
        if (args.length > 1) {
            // 参数2
            Boolean forceToGenAll = Boolean.valueOf(args[1]);
            if (forceToGenAll) {
                // 删除序列化文件
                createNewFile(bookMarkSeriFilePath);
            }
        }
        // step1 反序列化文件
        Long time = System.currentTimeMillis();
        deserializePDFFiles(bookMarkSeriFilePath);


        File file = new File(targetDir);
        File bookrmarkFile = new File(bookmarkFilePath);
        // step2生成文件
        // 注意这里 file.getAbsolutePath windows下返回的 路径是D:\tempFiles\pdftest 所以windows下必须要使用\ 作为路径分割符号
        genPDFbookmark(file, file.getAbsolutePath());
        seriableizeToFile(bookMarkSeriFilePath);
        writeToDesBookmark();

    }

    private static void init(String arg1) {
        targetDir = arg1;
        if (SystemUtils.isMacOs() || SystemUtils.isLinux()) {
            if (!targetDir.contains("/")) {
                throw new RuntimeException("Linux ，mac操作系统使用/分割路径：" + targetDir);
            }
            separator = "/";
            regSeparator="/";
        } else {
            if (!targetDir.contains("\\")) {
                // E:\programme\python\书籍
                throw new RuntimeException("windows系统请使用\\分割路径:" + targetDir + "示例：D:\\tempFiles\\pdftest");
            }
            //也就是第一个反斜杠是作为转义符存在的，第二个才是真正意义上的反斜杠,
            //所以在字符串中要表示字符’\’要用“\\”来表示
            //正则表达式匹配一个反斜杠，需要四个反斜杠"\\\\"
            //在字符串中，两个反斜杠被解释为一个反斜杠，
            //然后在作为正则表达式， \\ 则被正则表达式引擎解释为 \，所以在正则表达式中需要使用四个反斜杠
            separator = "\\";
            regSeparator="\\\\";
        }

        if (arg1.endsWith("/") || arg1.endsWith("\\")) {
            throw new RuntimeException("参数1 不需要带\\或者/结尾");
        } else {
            if (arg1.contains("/")) {
                bookMarkSeriFilePath = arg1 + "/" + bookMarkSeriFileName;
                bookmarkFilePath = arg1 + "/" + bookmarkFileName;
            } else {
                bookMarkSeriFilePath = arg1 + "\\" + bookMarkSeriFileName;
                bookmarkFilePath = arg1 + "\\" + bookmarkFileName;
            }
        }
        System.out.println("书签序列化后存储文件路径：" + bookMarkSeriFilePath);
        System.out.println("书籍书签.txt路径：：" + bookmarkFilePath);
    }

    /**
     * 生成最终的目标书签
     */
    private static void writeToDesBookmark() {
        // 删除之前的  书籍书签.txt
        createNewFile(bookmarkFilePath);
        // 遍历所有的书签目录
        Iterator<Map.Entry<String, CategoryInfo>> iterator = categoryInfoMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CategoryInfo> next = iterator.next();
            CategoryInfo categoryInfo = next.getValue();
            writeAllToBookrmarkFile(new File(bookmarkFilePath), categoryInfo.getCategoryBookmarkDir());
        }
    }

    /**
     * 生成分类总书签
     */
    private static void writeCategoryBookmark() {
        // 遍历所有的书签目录
        Iterator<Map.Entry<String, CategoryInfo>> iterator = categoryInfoMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CategoryInfo> next = iterator.next();
            CategoryInfo categoryInfo = next.getValue();
            // 判断是否需要更新总书签
            needToReGenCategoryBookmark(categoryInfo);
            if (categoryInfo.needReGenCategoryBookmark) {
                // 遍历 分类书签目录下的书签文件 写入总书签
                // 删除分类 总书签
                new File(categoryInfo.getCategoryBookmarkFilePath()).delete();

                writeAllToBookrmarkFile(new File(categoryInfo.getCategoryBookmarkFilePath()), categoryInfo.getCategoryBookmarkDir());

            } else {
                System.out.println("分类总书签不需要更新：" + categoryInfo.getCategoryBookmarkFilePath());
            }

        }
    }

    /**
     * 将 指定路径下的 书签写入到 指定的文件
     *
     * @param bookrmarkFile
     * @param targetDir
     */
    private static void writeAllToBookrmarkFile(File bookrmarkFile, String targetDir) {
        File targetDirFile = new File((targetDir));
        if (!targetDir.contains("书签")) {
            throw new RuntimeException("只能遍历 书签目录下下的文件，targetDir:" + targetDir + " 不是一个书签目录");
        }
        if (targetDirFile.exists() && targetDirFile.isDirectory()) {
            File[] files = targetDirFile.listFiles();
            for (File file : files) {
                // 分类目录下的 txt文件，这个txt文件 所在的目录必须包含 书签两个字， 且文件名不能包含总书签三个字
                if (file.isFile() &&
                        file.getName().endsWith(".txt")
                        && !file.getName().contains("总书签")
                        && !file.getName().contains(SpellHelper.getEname("总书签"))
                        && !file.getName().contains("zongshuqian")
                        && file.getAbsolutePath().contains("书签")) {

                    if (!allPDFBookMarkSet.contains(file.getAbsolutePath())) {// 注意上文限定了只能遍历书签目录， 否则这里不能乱删除文件
                        // 如果当前的书签文件 不是一个有效的书签，则删除该书签
                        file.delete();
                        System.out.println("书签：" + file.getAbsolutePath() + "不是一个有效的书签，删除该书签");
                        continue;
                    }
                    System.out.println("文件" + file.getAbsolutePath() + "写入到总文件" + bookrmarkFile.getAbsolutePath());
                    appendFromFileToFile(file.getAbsolutePath(), bookrmarkFile.getAbsolutePath());
                } else {
                    if (file.isFile()) {
                        //  System.out.println("总书签文件不需要写入");
                    }
                }
            }

        }
    }

    private static void needToReGenCategoryBookmark(CategoryInfo categoryInfo) {
        // 判断是否需要重新生成 分类总书签
        File fileDir = new File(categoryInfo.getCategoryBookmarkDir());
        if (fileDir.exists() && fileDir.isDirectory()) {
            File[] files = fileDir.listFiles();
            for (File file : files) {
                // 分类目录下的 txt文件，这个txt文件 所在的目录必须包含 书签两个字， 且文件名不能包含总书签三个字
                if (file.isFile() &&
                        file.getName().endsWith(".txt")
                        && !file.getName().contains("总书签")
                        && !file.getName().contains(SpellHelper.getEname("总书签"))
                        && !file.getName().contains("zongshuqian")
                        && file.getAbsolutePath().contains("书签")) {

                    if (!allPDFBookMarkSet.contains(file.getAbsolutePath())) {
                        // 如果当前的书签文件 不是一个有效的书签，则删除该书签,且需要更新总书签
                        file.delete();
                        System.out.println("书签：" + file.getAbsolutePath() + "不是一个有效的书签，删除该书签");
                        categoryInfo.setNeedReGenCategoryBookmark(true);
                    }
                } else {

                }
            }

        }
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

    private static void genPDFbookmark(File file, String targetDir) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            Arrays.stream(files).forEach(fileItem -> {
                genPDFbookmark(fileItem, targetDir);
            });
        } else {
            extraPdfBookmark(file, targetDir);
        }
    }

    public static void extraPdfBookmark(File file, String targetDir) {
        // 判断文件是否是PDF文件
        PDDocument document = null;
        String filePath = file.getPath();// D:\tempFiles\pdftest\docker\docker start I Docker Documentation.pdf
        String fileName = file.getName();// 测试.pdf
        if (!fileName.endsWith(".pdf")) {
            return;
        }
        String fileNameWithoutExtend = fileName.substring(0, fileName.length() - 4);
        try {
            // 书籍的分类目录
            String fileCategory = "";
            //  D:\tempFiles\pdftest\docker\docker start I Docker Documentation.pdf    D:\tempFiles\pdftest  --->docker\docker start I Docker Documentation.pdf  -->(docker,docker start I Docker Documentation.pdf)
            // 文件相对于目标路径的 路径
            String fileRelativeTargetPath = filePath.substring(targetDir.length() + 1, filePath.length());
            // 文件的分类
            String[] splitTags = fileRelativeTargetPath.split(regSeparator);

            if (splitTags.length > 1) {
                fileCategory = splitTags[0];
            } else {
                fileCategory = "";
            }
            // 生成文件的id，文件的id 使用 文件绝对路径 剔除targetPath，然后去除 路径分隔符后的字符串
            // 文件id 和系统无关，根据文件id，在cache中获取到这个文件对应的File对象，然后获取上次修改的时间戳。
            String fileId = fileRelativeTargetPath.replaceAll(regSeparator, ":");

            // 文件名对应的书签
            String bookmarkFilePath = bookmarkFilePath(fileCategory, fileNameWithoutExtend);
            // 书签目录文件夹
            String categoryBookmarkDir = categoryBookmarkDir(fileCategory);
            // 分类的总书签
            String categoryBookmarkFilePath = categoryBookmarkFilePath(fileCategory);

            // 记录所有的有效书签路径
            allPDFBookMarkSet.add(bookmarkFilePath);
            categoryInfoMap.putIfAbsent(categoryBookmarkDir, new CategoryInfo(categoryBookmarkDir, categoryBookmarkDir, categoryBookmarkFilePath, false));

            long lastModified = file.lastModified();
            PDFFile dbPdfFile = cache.get(fileId);
            if (dbPdfFile != null && dbPdfFile.getLastModifyTime() >= lastModified) {
                return;
            }
            System.out.println(filePath + "修改过，需要重新生成书签");
            // 标记分类总书签需要重新整理
            categoryInfoMap.get(categoryBookmarkDir).setNeedReGenCategoryBookmark(true);

            File bookmarkFile = new File(bookmarkFilePath);
            if (bookmarkFile.exists()) {
                bookmarkFile.delete();
            }
            PDFFile pdfFile = new PDFFile(fileId, filePath, fileName, fileCategory, lastModified, bookmarkFilePath, categoryBookmarkFilePath);

            cache.put(fileId, pdfFile);
            // 加载PDF
            document = PDDocument.load(file);
            COSDocument cos = document.getDocument();
            // PDF文件的书签内容大纲
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n书名：《" + fileName + "》", "UTF-8", true);
            if (outline != null) {
                Integer level = 0;    // A nesting level
                writeBookMark(document, outline, level, bookmarkFile, fileNameWithoutExtend);
            } else {
                // 没有大纲内容，只写入 文件名
                // FileUtils.writeStringToFile(new File(bookmarkFile.getAbsolutePath()), "\n书名：《" + fileName + "》", "UTF-8", true);
            }
        } catch (Exception e) {
            System.out.println("提取PDF文件 书签出现异常：" + filePath);
            cache.remove(filePath);
            e.printStackTrace();
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }


    }

    private static String bookmarkFilePath(String fileCategory, String fileNameWithoutExtend) {
        StringBuilder bookmarkFileBuilder = new StringBuilder();
        bookmarkFileBuilder.append(targetDir).append(separator);
        if (StringUtils.isNotEmpty(fileCategory)) {
            bookmarkFileBuilder.append(fileCategory).append(separator);
        }
        bookmarkFileBuilder.append("书签").append(separator).append(SpellHelper.getEname(fileNameWithoutExtend)).append(".txt");
        return bookmarkFileBuilder.toString();
    }

    /**
     * 书签目录
     *
     * @param fileCategory
     * @return
     */
    private static String categoryBookmarkDir(String fileCategory) {
        StringBuilder categoryBookmarkDir = new StringBuilder();
        categoryBookmarkDir.append(targetDir).append(separator);
        if (StringUtils.isNotEmpty(fileCategory)) {

            categoryBookmarkDir.append(fileCategory).append(separator);
        }
        categoryBookmarkDir.append("书签");
        return categoryBookmarkDir.toString();
    }

    private static String categoryBookmarkFilePath(String fileCategory) {
        StringBuilder categoryBookmarkFileBuilder = new StringBuilder();
        categoryBookmarkFileBuilder.append(targetDir).append(separator);
        if (StringUtils.isNotEmpty(fileCategory)) {

            categoryBookmarkFileBuilder.append(fileCategory).append(separator);
        }
        categoryBookmarkFileBuilder.append("书签").append(separator).append("001" + fileCategory + "-总书签").append(".txt");
        return categoryBookmarkFileBuilder.toString();
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
            if (!StringUtils.isEmpty(file.getId())) {
                cache.put(file.getFilePath(), file);
            }
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
