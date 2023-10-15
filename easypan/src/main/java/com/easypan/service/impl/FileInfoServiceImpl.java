package com.easypan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.easypan.config.AppConfig;
import com.easypan.convert.FileInfoConvert;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.*;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.exception.ErrorCode;
import com.easypan.exception.FastException;
import com.easypan.mappers.FileInfoDao;
import com.easypan.mappers.UserInfoDao;
import com.easypan.page.PageResult;
import com.easypan.query.FileInfoQuery;
import com.easypan.service.FileInfoService;
import com.easypan.utils.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件信息表
 *
 * @author Tao MrHaoTao@gmail.com
 * @since 1.0.0 2023-08-25
 */
@Service
public class FileInfoServiceImpl extends BaseServiceImpl<FileInfoDao, FileInfo> implements FileInfoService {

    private static final Logger logger = LoggerFactory.getLogger(FileInfoServiceImpl.class);

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private FileInfoDao fileInfoDao;

    @Resource
    private UserInfoDao userInfoDao;

    @Resource
    private AppConfig appConfig;

    @Resource
    @Lazy
    private FileInfoServiceImpl fileInfoService;

    @Override
    public PageResult<FileInfoVO> page(FileInfoQuery query) {
        IPage<FileInfo> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), page.getPages(), FileInfoConvert.INSTANCE.convertList(page.getRecords()));
    }

    private QueryWrapper<FileInfo> getWrapper(FileInfoQuery query) {
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq(StringUtils.isNotEmpty(query.getUserId()), "user_id", query.getUserId());
        wrapper.eq(StringUtils.isNotEmpty(query.getFileId()), "file_id", query.getFileId());
        wrapper.eq(StringUtils.isNotEmpty(query.getFilePid()), "file_pid", query.getFilePid());
        wrapper.eq(StringUtils.isNotEmpty(query.getFileName()), "file_name", query.getFileName());
        wrapper.eq(StringUtils.isNotEmpty(query.getFileMd5()), "file_md5", query.getFileMd5());
        wrapper.in(query.getFileIdArray() != null, "file_id", query.getFileIdArray());
        wrapper.notIn(query.getExcludeFileIdArray() != null, "file_id", query.getExcludeFileIdArray());
        wrapper.eq(query.getFileType() != null, "file_type", query.getFileType());
        wrapper.eq(query.getFolderType() != null, "folder_type", query.getFolderType());
        wrapper.eq(query.getDelFlag() != null, "del_flag", query.getDelFlag());
        wrapper.eq(query.getFileCategory() != null, "file_category", query.getFileCategory());
        wrapper.last(query.getStart() != null && query.getEnd() != null, "limit " + query.getStart() + "," + query.getEnd());
        wrapper.last(StringUtils.isNotEmpty(query.getOrderBy()), "order by " + query.getOrderBy());
        return wrapper;
    }

    @Override
    public void save(FileInfoVO vo) {
        FileInfo entity = FileInfoConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);
    }

    @Override
    public void update(FileInfoVO vo) {
        FileInfo entity = FileInfoConvert.INSTANCE.convert(vo);

        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto uploadFile(SessionWebUserDto sessionWebUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5, Integer chunkIndex, Integer chunks) {
        UploadResultDto uploadResultDto = new UploadResultDto();
        boolean uploadSuccess = true;
        File tempFileFolder = null;
        Date curDate = new Date();

        try {
            if (StringUtils.isEmpty(fileId)) {
                fileId = StringUtils.getRandomString(Constants.LENGTH_10);
            }
            uploadResultDto.setFileId(fileId);

            UserSpaceDto userSpaceDto = redisComponent.getUserUseSpace(sessionWebUserDto.getUserId());

            if (chunkIndex == 0) {
                FileInfoQuery infoQuery = new FileInfoQuery();
                infoQuery.setFileMd5(fileMd5);
                infoQuery.setStart(0);
                infoQuery.setEnd(1);
                infoQuery.setStatus(FileStatusEnums.USING.getStatus());
                List<FileInfo> dbFileList = list(getWrapper(infoQuery));
                // 秒传
                if (!dbFileList.isEmpty()) {
                    FileInfo dbFile = dbFileList.get(0);
                    //判断文件大小
                    if (dbFile.getFileSize() + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                        throw new FastException(ErrorCode.CODE_904);
                    }
                    dbFile.setFileId(fileId);
                    dbFile.setFilePid(filePid);
                    dbFile.setUserId(sessionWebUserDto.getUserId());
                    dbFile.setCreateTime(curDate);
                    dbFile.setLastUpdateTime(curDate);
                    dbFile.setStatus(FileStatusEnums.USING.getStatus());
                    dbFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
                    dbFile.setFileMd5(fileMd5);
                    //文件重命名
                    fileName = autoRename(filePid, sessionWebUserDto.getUserId(), fileName);
                    dbFile.setFileName(fileName);
                    //入库
                    save(dbFile);
                    uploadResultDto.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
                    //更新用户使用空间
                    updateUserSpace(sessionWebUserDto, dbFile.getFileSize());
                    return uploadResultDto;
                }
            }

            //判断磁盘空间
            Long currentTempSize = redisComponent.getFileTempSize(sessionWebUserDto.getUserId(), fileId);
            if (currentTempSize + file.getSize() + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                throw new FastException(ErrorCode.CODE_904);
            }

            //暂存临时目录
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = sessionWebUserDto.getUserId() + fileId;

            tempFileFolder = new File(tempFolderName + currentUserFolderName);
            if (!tempFileFolder.exists()) {
                tempFileFolder.mkdirs();
            }

            File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
            file.transferTo(newFile);

            if (chunkIndex < chunks - 1) {
                uploadResultDto.setStatus(UploadStatusEnums.UPLOADING.getCode());
                //保存临时大小
                redisComponent.saveFileTempSize(sessionWebUserDto.getUserId(), fileId, file.getSize());
                return uploadResultDto;
            }

            redisComponent.saveFileTempSize(sessionWebUserDto.getUserId(), fileId, file.getSize());

            //最后一个分片上传完成，入库，异步合并分片
            String month = DateUtils.format(new Date(), DateTimePatternEnum.YYYYMM.getPattern());
            String fileSuffix = StringUtils.getFileSuffix(fileName);
            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            FileTypeEnums fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
            //自动重命名
            fileName = autoRename(filePid, sessionWebUserDto.getUserId(), fileName);

            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileId(fileId);
            fileInfo.setUserId(sessionWebUserDto.getUserId());
            fileInfo.setFileName(fileName);
            fileInfo.setFileMd5(fileMd5);
            fileInfo.setFilePath(month + "/" + realFileName);
            fileInfo.setFilePid(filePid);
            fileInfo.setCreateTime(curDate);
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
            fileInfo.setFileType(fileTypeEnum.getType());
            fileInfo.setStatus(FileStatusEnums.TRANSFER.getStatus());
            fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            save(fileInfo);

            Long totalSize = redisComponent.getFileTempSize(sessionWebUserDto.getUserId(), fileId);
            updateUserSpace(sessionWebUserDto, totalSize);

            uploadResultDto.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileInfoService.transferFile(fileInfo.getFileId(), sessionWebUserDto);
                }
            });

        } catch (FastException e) {
            logger.error("文件上传失败", e);
            uploadSuccess = false;
            throw e;
        } catch (Exception e) {
            logger.error("文件上传失败", e);
            uploadSuccess = false;
        } finally {
            if (!uploadSuccess && tempFileFolder != null) {
                try {
                    FileUtils.deleteDirectory(tempFileFolder);
                } catch (IOException e) {
                    logger.error("删除临时目录失败", e);
                }
            }
        }
        return uploadResultDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFile2RecycleBatch(String userId, String fileIds) {
        FileInfoQuery query = new FileInfoQuery();
        String[] fileIdArray = fileIds.split(",");
        query.setFileIdArray(fileIdArray);
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        List<FileInfo> fileInfoList = this.list(query);
        if (fileInfoList.isEmpty()) {
            return;
        }
        // 如果不为空
        List<String> delFilePidList = new ArrayList<>();
        fileInfoList.stream()
                .filter(fileInfo ->
                        fileInfo.getFolderType().equals(FileFolderTypeEnums.FOLDER.getType()))
                .forEach(fileInfo ->
                        findAllSubFolderFileIdList(delFilePidList, userId, fileInfo.getFileId(), FileDelFlagEnums.USING.getFlag()));

        //将目录下的所有文件更新为已删除
        if (!delFilePidList.isEmpty()) {
            FileInfo updateInfo = new FileInfo();
            updateInfo.setDelFlag(FileDelFlagEnums.DEL.getFlag());
            this.baseMapper.updateFileDelFlagBatch(updateInfo, userId, delFilePidList,
                    null, FileDelFlagEnums.USING.getFlag());
        }

        //将选中的文件更新为回收站
        List<String> delFileIdList = Arrays.asList(fileIds.split(","));
        FileInfo fileInfo = new FileInfo();
        fileInfo.setRecoveryTime(new Date());
        fileInfo.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        this.baseMapper.updateFileDelFlagBatch(fileInfo, userId, null,
                delFileIdList, FileDelFlagEnums.USING.getFlag());
    }

    @Override
    public FileInfo newFolder(String filePid, String userId, String fileName) {
        checkFileName(filePid, userId, fileName, FileFolderTypeEnums.FOLDER.getType());

        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(StringUtils.getRandomString(Constants.LENGTH_10));
        fileInfo.setUserId(userId);
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(fileName);
        fileInfo.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setStatus(FileStatusEnums.USING.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());

        this.save(fileInfo);
        return fileInfo;
    }

    @Override
    public List<FileInfo> list(FileInfoQuery query) {
        return list(getWrapper(query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVO rename(String fileId, String userId, String fileName) {
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("file_id", fileId);
        wrapper.eq("user_id", userId);
        FileInfo fileInfo = this.getOne(wrapper);
        if (null == fileInfo) {
            throw new FastException("文件不存在！");
        }
        String filePid = fileInfo.getFilePid();
        checkFileName(filePid, userId, fileName, fileInfo.getFolderType());
        //获取文件后缀
        if (FileFolderTypeEnums.FILE.getType().equals(fileInfo.getFolderType())) {
            fileName = fileName + StringUtils.getFileSuffix(fileInfo.getFileName());
        }
        Date curDate = new Date();
        fileInfo.setFileName(fileName);
        fileInfo.setLastUpdateTime(curDate);
        this.saveOrUpdate(fileInfo);

        FileInfoQuery query = new FileInfoQuery();
        query.setFilePid(filePid);
        query.setUserId(userId);
        query.setFileName(fileName);
        long count = this.count(getWrapper(query));
        if (count > 1) {
            throw new FastException("文件名" + fileName + "已存在！");
        }

        return FileInfoConvert.INSTANCE.convert(fileInfo);
    }

    @Override
    public void changeFileFolder(String fileIds, String filePid, String userId) {
        if (fileIds.equals(filePid)) {
            throw new FastException(ErrorCode.CODE_600);
        }
        if (!Constants.ZERO_STR.equals(filePid)) {
            QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
            wrapper.eq("file_id", filePid);
            wrapper.eq("user_id", userId);
            FileInfo fileInfo = this.getOne(wrapper);

            if (null == fileInfo || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())) {
                throw new FastException(ErrorCode.CODE_600);
            }

            String[] fileIdArray = fileIds.split(",");

            FileInfoQuery query = new FileInfoQuery();
            query.setFilePid(filePid);
            query.setUserId(userId);
            List<FileInfo> dbFileList = this.list(query);

            Map<String, FileInfo> dbFileMap = dbFileList.stream().collect(
                    Collectors.toMap(FileInfo::getFileName, Function.identity(), (data1, data2) -> data2));

            //查询选中文件
            query = new FileInfoQuery();
            query.setUserId(userId);
            query.setFileIdArray(fileIdArray);
            List<FileInfo> selectFileList = this.list(query);

            //将所选文件重命名
            for (FileInfo item : selectFileList) {
                FileInfo rootFileInfo = dbFileMap.get(item.getFileName());
                if (null != rootFileInfo) {
                    String fileName = StringUtils.rename(item.getFileName());
                    item.setFileName(fileName);
                }
                item.setFilePid(filePid);
                this.saveOrUpdate(item);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFileBatch(String userId, String fileIds) {
        // 首先将选中的需还原的文件查找出来
        FileInfoQuery query = new FileInfoQuery();
        String[] fileIdArray = fileIds.split(",");
        query.setFileIdArray(fileIdArray);
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        List<FileInfo> fileInfoList = this.list(query);

        //delFileSubFolderFileIdList为所有文件夹的id
        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        //找到所选文件子目录文件ID

        // 如果是文件夹，递归找出该文件夹中的所有文件夹
        fileInfoList.stream()
                .filter(fileInfo ->
                        fileInfo.getFolderType().equals(FileFolderTypeEnums.FOLDER.getType()))
                .forEach(fileInfo ->
                        findAllSubFolderFileIdList(delFileSubFolderFileIdList, userId,
                                fileInfo.getFileId(), FileDelFlagEnums.USING.getFlag()));

        // 查询所有跟目录的文件准备判断是否需要重命名
        query = new FileInfoQuery();
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        query.setFilePid(Constants.ZERO_STR);
        List<FileInfo> allRootFileList = this.list(query);

        Map<String, FileInfo> rootFileMap =
                allRootFileList.stream().
                        collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));

        // 将目录下的所有删除的文件更新为正常
        if (!delFileSubFolderFileIdList.isEmpty()) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            this.baseMapper.updateFileDelFlagBatch(fileInfo, userId, delFileSubFolderFileIdList,
                    null, FileDelFlagEnums.DEL.getFlag());
        }

        // 将选中的文件更新为正常,且父级目录到跟目录
        List<String> delFileIdList = Arrays.asList(fileIds.split(","));
        FileInfo fileInfo = new FileInfo();
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfo.setFilePid(Constants.ZERO_STR);
        fileInfo.setLastUpdateTime(new Date());
        this.baseMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList,
                FileDelFlagEnums.RECYCLE.getFlag());

        //将所选文件重命名
        for (FileInfo item : fileInfoList) {
            // 从map中查找名字相同的文件
            FileInfo rootFileInfo = rootFileMap.get(item.getFileName());
            //文件名已经存在，重命名被还原的文件名
            if (rootFileInfo != null) {
                String fileName = StringUtils.rename(item.getFileName());
                FileInfo updateInfo = new FileInfo();
                updateInfo.setFileName(fileName);
                updateInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
                updateInfo.setFilePid(Constants.ZERO_STR);
                updateInfo.setLastUpdateTime(new Date());
                this.baseMapper.updateByFileIdAndUserId(updateInfo, item.getFileId(), userId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFileBatch(String userId, String fileIds, boolean adminOp) {

        FileInfoQuery query = new FileInfoQuery();
        query.setFileIdArray(fileIds.split(","));
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        List<FileInfo> fileInfoList = this.list(query);

        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        //找到所选文件子目录文件ID
        fileInfoList.stream()
                .filter(fileInfo ->
                        fileInfo.getFolderType().equals(FileFolderTypeEnums.FOLDER.getType()))
                .forEach(fileInfo ->
                        findAllSubFolderFileIdList(delFileSubFolderFileIdList, userId,
                                fileInfo.getFileId(), FileDelFlagEnums.DEL.getFlag()));


        //删除所选文件，子目录中的文件
        if (!delFileSubFolderFileIdList.isEmpty()) {
            this.baseMapper.delFileBatch(userId, delFileSubFolderFileIdList, null, adminOp
                    ? null : FileDelFlagEnums.DEL.getFlag());
        }
        //删除所选文件
        this.baseMapper.delFileBatch(userId, null, Arrays.asList(fileIds.split(",")),
                adminOp ? null : FileDelFlagEnums.RECYCLE.getFlag());

        Long useSpace = this.baseMapper.selectUseSpace(userId);
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUseSpace(useSpace);
        userInfoDao.updateById(userInfo);
        //设置缓存
        UserSpaceDto userSpaceDto = redisComponent.getUserUseSpace(userId);
        userSpaceDto.setUseSpace(useSpace);
        redisComponent.saveUserUseSpace(userId, userSpaceDto);

    }

    @Override
    public void saveShare(String shareRootFilePid, String shareFileIds, String myFolderId, String shareUserId, String currentUserId) {
        String[] shareFileIdArray = shareFileIds.split(",");
        //目标目录文件列表
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(currentUserId);
        fileInfoQuery.setFilePid(myFolderId);
        // 目标文件夹下所有文件
        List<FileInfo> currentFileList = this.list(fileInfoQuery);
        // 映射成map
        Map<String, FileInfo> currentFileMap =
                currentFileList.stream()
                        .collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //选择的文件
        fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(shareUserId);
        fileInfoQuery.setFileIdArray(shareFileIdArray);
        // 选中的所有文件
        List<FileInfo> shareFileList = this.list(fileInfoQuery);

        //重命名选择的文件
        List<FileInfo> copyFileList = new ArrayList<>();
        Date curDate = new Date();
        for (FileInfo item : shareFileList) {
            FileInfo haveFile = currentFileMap.get(item.getFileName());
            if (haveFile != null) {
                item.setFileName(StringUtils.rename(item.getFileName()));
            }
            // 需要找出所有文件复制
            findAllSubFile(copyFileList, item, shareUserId, currentUserId, curDate, myFolderId);
        }
//        System.out.println(copyFileList.size());
        this.saveBatch(copyFileList);
    }

    private void findAllSubFile(List<FileInfo> copyFileList, FileInfo fileInfo, String sourceUserId,
                                String currentUserId, Date curDate, String newFilePid) {
        // 将文件添加进集合
        String sourceFileId = fileInfo.getFileId();
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setFilePid(newFilePid);
        fileInfo.setUserId(currentUserId);
        String newFileId = StringUtils.getRandomString(Constants.LENGTH_10);
        fileInfo.setFileId(newFileId);
        copyFileList.add(fileInfo);
        // 如果是目录的话，递归添加
        if (FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())) {
            FileInfoQuery query = new FileInfoQuery();
            query.setFilePid(sourceFileId);
            query.setUserId(sourceUserId);
            List<FileInfo> sourceFileList = this.list(query);

            sourceFileList.forEach(item -> findAllSubFile(copyFileList, item, sourceUserId, currentUserId, curDate, newFileId));
        }
    }

    private void checkFileName(String filePid, String userId,
                               String fileName, Integer folderType) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFolderType(folderType);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfoQuery.setStart(0);
        fileInfoQuery.setEnd(1);
        List<FileInfo> fileList = list(getWrapper(fileInfoQuery));
        if (!fileList.isEmpty()) {
            throw new FastException("此目录下已存在同名文件，请修改名称");
        }
    }

    private String autoRename(String filePid, String userId, String fileName) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());

        long count = count(getWrapper(fileInfoQuery));
        if (count > 0) {
            fileName = StringUtils.rename(fileName);
        }
        return fileName;
    }

    private void updateUserSpace(SessionWebUserDto webUserDto, long useSpace) {
        Integer count = userInfoDao.updateUseSpace(webUserDto.getUserId(), useSpace, null);
        if (count == 0) {
            throw new FastException(ErrorCode.CODE_904);
        }
        UserSpaceDto spaceDto = redisComponent.getUserUseSpace(webUserDto.getUserId());
        spaceDto.setUseSpace(spaceDto.getUseSpace() + useSpace);
        redisComponent.saveUserUseSpace(webUserDto.getUserId(), spaceDto);
    }

    /**
     * 文件转码
     *
     * @param String            fileId
     * @param SessionWebUserDto webUserDto
     * @return void
     */
    @Async
    public void transferFile(String fileId, SessionWebUserDto webUserDto) {
        boolean transferSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileTypeEnums fileTypeEnums = null;
        QueryWrapper<FileInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", webUserDto.getUserId()).eq("file_id", fileId);
        FileInfo fileInfo = getOne(wrapper);

        try {
            if (fileInfo == null || !FileStatusEnums.TRANSFER.getStatus().equals(fileInfo.getStatus())) {
                return;
            }
            //临时目录
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + currentUserFolderName);
            String fileSuffix = StringUtils.getFileSuffix(fileInfo.getFileName());
            String month = DateUtils.format(fileInfo.getCreateTime(), DateTimePatternEnum.YYYYMM.getPattern());
            //目标目录
            String targetFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFolder = new File(targetFolderName + month);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }
            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            targetFilePath = targetFolder + "/" + realFileName;
            //合并文件
            union(fileFolder.getPath(), targetFilePath, fileInfo.getFileName(), true);

            fileTypeEnums = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
            if (FileTypeEnums.VIDEO == fileTypeEnums) {
                //切割视频文件
                cutFile4Video(fileId, targetFilePath);
                //生成视频缩略图
                cover = month + "/" + currentUserFolderName + Constants.IMAGE_PNG_SUFFIX;
                String coverPath = targetFolderName + "/" + cover;
                ScaleFilter.createCover4Video(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath));
            } else if (FileTypeEnums.IMAGE == fileTypeEnums) {
                //生成缩略图
                cover = month + "/" + realFileName.replace(".", "_.");
                String coverPath = targetFolderName + "/" + cover;
                Boolean created = ScaleFilter.createThumbnailWidthFFmpeg(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath), false);
                if (!created) {
                    FileUtils.copyFile(new File(targetFilePath), new File(coverPath));
                }
            }
        } catch (Exception e) {
            logger.error("文件转码失败，文件ID：{}，用户ID：{}", fileId, webUserDto.getUserId(), e);
            transferSuccess = false;
        } finally {
            FileInfo updateInfo = getOne(wrapper);
            updateInfo.setFileSize(new File(targetFilePath).length());
            updateInfo.setFileCover(cover);
            updateInfo.setStatus(transferSuccess ? FileStatusEnums.USING.getStatus() : FileStatusEnums.TRANSFER_FAIL.getStatus());
            fileInfoDao.update(updateInfo, wrapper);
        }
    }

    private void cutFile4Video(String fileId, String filePath) {
        //创建同名切片目录
        File tsFolder = new File(filePath.substring(0, filePath.lastIndexOf(".")));
        if (!tsFolder.exists()) {
            tsFolder.mkdirs();
        }

        String tsPath = tsFolder + "/" + Constants.TS_NAME;
        String m3u8Path = tsFolder.getPath() + "/" + Constants.M3U8_NAME;

        // 方式1: 直接切片（-c copy，速度快，要求视频编码为H.264）
        final String CMD_CUT_TS_DIRECT = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";
        // 方式2: 转码切片（兼容所有编码，速度较慢）
        final String CMD_CUT_TS_TRANSCODE = "ffmpeg -i %s -c:v libx264 -c:a aac -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";

        // 先尝试直接切片（快速）
        String cmd = String.format(CMD_CUT_TS_DIRECT, filePath, m3u8Path, tsFolder.getPath(), fileId);
        String result = ProcessUtils.executeCommand(cmd, false);

        // 检查m3u8是否生成成功，如果失败则使用转码方式
        File m3u8File = new File(m3u8Path);
        if (!m3u8File.exists() || m3u8File.length() == 0) {
            logger.info("直接切片失败，尝试转码切片，文件：{}", filePath);
            cmd = String.format(CMD_CUT_TS_TRANSCODE, filePath, m3u8Path, tsFolder.getPath(), fileId);
            ProcessUtils.executeCommand(cmd, false);
        }
        // 转码后再次检查，如果仍然失败则抛出异常
        if (!m3u8File.exists() || m3u8File.length() == 0) {
            throw new FastException("视频转码失败，m3u8文件未生成");
        }
    }

    /**
     * 文件合并
     *
     * @param String  dirPath
     * @param String  toFilePath
     * @param String  fileName
     * @param boolean delSource
     * @return void
     */
    private void union(String dirPath, String toFilePath, String fileName, boolean delSource) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new FastException("目录不存在");
        }

        File[] fileList = dir.listFiles();
        File targetFile = new File(toFilePath);
        RandomAccessFile writeFile = null;
        try {
            writeFile = new RandomAccessFile(targetFile, "rw");
            byte[] b = new byte[1024 * 10];
            for (int i = 0; i < Objects.requireNonNull(fileList).length; i++) {
                int len = -1;
                File chunkFile = new File(dirPath + "/" + i);
                RandomAccessFile readFile = null;
                try {
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(b)) != -1) {
                        writeFile.write(b, 0, len);
                    }
                } catch (Exception e) {
                    logger.error("合并分片失败", e);
                    throw new FastException("合并分片失败");
                } finally {
                    readFile.close();
                }
            }
        } catch (Exception e) {
            logger.error("合并文件：{}失败", fileName, e);
            throw new FastException("合并文件" + fileName + "失败");
        } finally {
            if (null != writeFile) {
                try {
                    writeFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (delSource && dir.exists()) {
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 递归查找文件夹下的所有文件
    private void findAllSubFolderFileIdList(List<String> fileIdList, String userId, String fileId, Integer delFlag) {
        // 首先将自己添加进删除集合
        fileIdList.add(fileId);

        // 然后查找自己下面的所有的文件夹
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFilePid(fileId);
        query.setDelFlag(delFlag);
        query.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        List<FileInfo> fileInfoList = this.list(query);

        for (FileInfo fileInfo : fileInfoList) {
            findAllSubFolderFileIdList(fileIdList, userId, fileInfo.getFileId(), delFlag);
        }
    }
}