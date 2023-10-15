package com.easypan.entity.vo;

//import io.swagger.v3.oas.annotations.media.Schema;

import com.easypan.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
* 文件信息表
*
* @author Tao MrHaoTao@gmail.com
* @since 1.0.0 2023-08-25
*/
@Data
//@Schema(description = "文件信息表")
public class FileInfoVO implements Serializable {
	private static final long serialVersionUID = 1L;

//	@Schema(description = "文件Id")
	private String fileId;

//	@Schema(description = "用户Id")
	private String userId;

//	@Schema(description = "用户昵称")
	private String nickName;

//	@Schema(description = "父级Id")
	private String filePid;

//	@Schema(description = "文件大小")
	private Long fileSize;

//	@Schema(description = "文件名")
	private String fileName;

//	@Schema(description = "文件封面")
	private String fileCover;


//	@Schema(description = "最后更新时间")
	@JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
	private Date lastUpdateTime;

//	@Schema(description = "0:文件 1:目录")
	private Integer folderType;

//	@Schema(description = "文件分类 1:视频 2:音频 3:图片 4:文档 5:其他")
	private Integer fileCategory;

//	@Schema(description = "1:视频 2:音频 3:图片 4:pdf 5:doc 6:excel 7:txt 8:code 9:zip 10:其他")
	private Integer fileType;

//	@Schema(description = "0:转码中 1:转码失败 2:转码成功")
	private Integer status;

}