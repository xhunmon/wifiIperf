package com.android.wifiiperf;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * user: uidq0530， 2017-08-22.
 * description：
 *
 * @author xhunmon
 */

public class CommandHelper {

    private static final String TAG = "CommandHelper";
    //default time out, in millseconds
    public static int DEFAULT_TIMEOUT = 8000;
    public static final int DEFAULT_INTERVAL = 1000;
    public static long START;
    public static CommandResult exec(String command) throws IOException, InterruptedException {
        Log.i(TAG,"exec command: "+command);
        //创建一个子进程，并保存在process对象中
        Process process = Runtime.getRuntime().exec(command);

        CommandResult commandResult = wait(process);
        Log.i(TAG,"CommandResult: "+commandResult.toString());
        if (process != null) {
            process.destroy();
        }
        return commandResult;
    }

    private static boolean isOverTime() {
        return System.currentTimeMillis() - START >= DEFAULT_TIMEOUT;
    }

    private static CommandResult wait(Process process) throws InterruptedException, IOException {
        BufferedReader errorStreamReader = null;
        BufferedReader inputStreamReader = null;

        try {
            errorStreamReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            inputStreamReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            //timeout control
            START = System.currentTimeMillis();
            boolean isFinished = false;
            Log.i(TAG,"wait isFinished: "+isFinished);
            for (;;) {
                if (isOverTime()) {
                    CommandResult result = new CommandResult();
                    result.setExitValue(CommandResult.EXIT_VALUE_TIMEOUT);
                    result.setOutput("Command process timeout");
                    return result;
                }
                Log.i(TAG,"wait  for(;;) isFinished: "+isFinished);

                if (isFinished) {
                    CommandResult result = new CommandResult();
                    result.setExitValue(process.waitFor());  //process.waitFor() 表示 等这条语句执行完后再往下执行

                    //parse error info
                    if (errorStreamReader.ready()) {
                        StringBuilder buffer = new StringBuilder();
                        String line;
                        while ((line = errorStreamReader.readLine()) != null) {
                            buffer.append(line);
                        }
                        result.setError(buffer.toString());
                        Log.i(TAG,"for(;;) error: "+buffer.toString());
                    }

                    //parse info
                    if (inputStreamReader.ready()) {
                        StringBuilder buffer = new StringBuilder();
                        String line;
                        while ((line = inputStreamReader.readLine()) != null) {
                            buffer.append(line);
                        }
                        result.setOutput(buffer.toString());
                        Log.i(TAG,"for(;;) parse: "+buffer.toString());
                    }
                    return result;
                }

                try {
                    isFinished = true;
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    // process hasn't finished yet
                    isFinished = false;
                    Thread.sleep(DEFAULT_INTERVAL);
                }
            }

        } finally {
            if (errorStreamReader != null) {
                try {
                    errorStreamReader.close();
                } catch (IOException e) {
                }
            }
            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
