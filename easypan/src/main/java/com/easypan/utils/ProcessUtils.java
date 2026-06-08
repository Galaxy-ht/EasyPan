package com.easypan.utils;

import com.easypan.exception.FastException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

//java执行cmd命令
@Slf4j
public class ProcessUtils {

    public static String executeCommand(String cmd, Boolean outprintLog) throws FastException {
        if (StringUtils.isEmpty(cmd)) {
            log.error("--- 指令执行失败，因为要执行的FFmpeg指令为空！ ---");
            return null;
        }

        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        try {
            // 使用 /bin/sh -c 执行命令，确保能找到系统PATH中的ffmpeg
            process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            // 执行ffmpeg指令
            // 取出输出流和错误流的信息
            // 注意：必须要取出ffmpeg在执行命令过程中产生的输出信息，如果不取的话当输出流信息填满jvm存储输出留信息的缓冲区时，线程就回阻塞住
            PrintStream errorStream = new PrintStream(process.getErrorStream());
            PrintStream inputStream = new PrintStream(process.getInputStream());
            errorStream.start();
            inputStream.start();
            // 等待ffmpeg命令执行完
            int exitCode = process.waitFor();
            // 等待流读取线程完成
            errorStream.join();
            inputStream.join();
            // 获取执行结果字符串
            String result = errorStream.stringBuffer.append(inputStream.stringBuffer + "\n").toString();

            if (exitCode != 0) {
                log.error("执行命令失败，exitCode:{}，命令:{}，结果:{}", exitCode, cmd, result);
                throw new FastException("视频转换失败");
            }

            if (outprintLog) {
                log.info("执行命令:{}，已执行完毕,执行结果:{}", cmd, result);
            } else {
                log.info("执行命令:{}，已执行完毕", cmd);
            }
            return result;
        } catch (FastException e) {
            throw e;
        } catch (IOException e) {
            log.warn("FFmpeg 未安装或不可用，跳过命令: {}", cmd);
            return null;
        } catch (Exception e) {
            log.error("执行命令失败:{} ", e.getMessage());
            throw new FastException("视频转换失败");
        } finally {
            if (null != process) {
                ProcessKiller ffmpegKiller = new ProcessKiller(process);
                runtime.addShutdownHook(ffmpegKiller);
            }
        }
    }

    /**
     * 在程序退出前结束已有的FFmpeg进程
     */
    private static class ProcessKiller extends Thread {
        private Process process;

        public ProcessKiller(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            this.process.destroy();
        }
    }


    /**
     * 用于取出ffmpeg线程执行过程中产生的各种输出和错误流的信息
     */
    static class PrintStream extends Thread {
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuffer stringBuffer = new StringBuffer();

        public PrintStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                if (null == inputStream) {
                    return;
                }
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuffer.append(line);
                }
            } catch (Exception e) {
                log.error("读取输入流出错了！错误信息：" + e.getMessage());
            } finally {
                try {
                    if (null != bufferedReader) {
                        bufferedReader.close();
                    }
                    if (null != inputStream) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    log.error("调用PrintStream读取输出流后，关闭流时出错！");
                }
            }
        }
    }
}
