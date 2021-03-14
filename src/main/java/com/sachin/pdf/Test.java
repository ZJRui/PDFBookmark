package com.sachin.pdf;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Sachin
 * @Date 2021/3/14
 **/
public class Test {

    public static void main(String[] args) throws Exception {
       // FileUtils.writeStringToFile(new File("G:\\programme\\ajax\\书籍ajax\\23\\text.txt"),"zhognwen ", "UTF-8", true);
       /* File file = new File("D:\\临时文件\\231\\8899.txt");
        if (file.exists()) {
            file.delete();
        }
        file.getParentFile().mkdirs();
        file.createNewFile();*/
        Pattern pattern = Pattern.compile(".a.");
        Matcher matcher = pattern.matcher("orders3.xls");
        boolean b = matcher.find();
        Matcher matcher2 = pattern.matcher("sales1.xls");
        boolean b2 = matcher2.find();
        System.out.println(b2);
        System.out.println(b);

    }
}
