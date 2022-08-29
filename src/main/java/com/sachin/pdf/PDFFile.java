package com.sachin.pdf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Author Sachin
 * @Date 2021/3/14
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("all")
public class PDFFile implements Serializable {

    private static final long serialVersionUID = -3704326372217269309L;
    private String filePath;//G:programme\java\dd.pdf
    private String fileName;//测试.pdf
    private String fileCategory;
    private Long lastModifyTime;
    private String bookmarkFilePath;//G:programme\java\书签\dd.txt  单个文件书签
    private String categoryBookmarkFilePath;//G:programme\java\书签\Add.txt 总的书签

}
