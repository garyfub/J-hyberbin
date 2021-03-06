/*
 * Copyright 2015 www.hyberbin.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Email:hyberbin@qq.com
 */
package org.jplus.hyb.log;

import java.text.MessageFormat;
import java.util.Date;
import org.jplus.util.ObjectHelper;

/**
 * 本地默认的日志类
 * @author hyberbin
 */
public class LocalLogger implements Logger {

    public static final int TRACE = 0;
    public static final int DEBUG = 1;
    public static final int INFO = 2;
    public static final int WARN = 3;
    public static final int ERROR = 4;
    private String name;
    private static int level = INFO;

    public void setName(String name) {
        this.name = name;
    }

    public static void setLevel(int level) {
        LocalLogger.level = level;
    }

    public static int getLevel() {
        return level;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return level <= TRACE;
    }

    @Override
    public void trace(String msg) {
        if (isTraceEnabled()) {
            println(TextFormat.format(msg));
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled()) {
            println(TextFormat.format(format, arg));
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (isTraceEnabled()) {
            println(TextFormat.format(format, arg1, arg2));
        }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (isTraceEnabled()) {
            println(TextFormat.format(format, arguments));
        }
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (isTraceEnabled()) {
            println(TextFormat.format(msg, t));
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return level <= DEBUG;
    }

    @Override
    public void debug(String msg) {
        if (isDebugEnabled()) {
           println(TextFormat.format(msg));
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            println(TextFormat.format(format, arg));
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            println(TextFormat.format(format, arg1, arg2));
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            println(TextFormat.format(format, arguments));
        }
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            println(TextFormat.format(msg, t));
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return level <= INFO;
    }

    @Override
    public void info(String msg) {
        if (isInfoEnabled()) {
            println(TextFormat.format(msg));
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            println(TextFormat.format(format, arg));
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            println(TextFormat.format(format, arg1, arg2));
        }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            println(TextFormat.format(format, arguments));
        }
    }

    @Override
    public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            println(TextFormat.format(msg, t));
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return level <= WARN;
    }

    @Override
    public void warn(String msg) {
        if (isWarnEnabled()) {
            println(TextFormat.format(msg));
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            println(TextFormat.format(format, arg));
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            println(TextFormat.format(format, arguments));
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            println(TextFormat.format(format, arg1, arg2));
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            println(TextFormat.format(msg, t));
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return level <= ERROR;
    }

    @Override
    public void error(String msg) {
        if (isErrorEnabled()) {
             println(TextFormat.format(msg));
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            println(TextFormat.format(format, arg));
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            println(TextFormat.format(format, arg1, arg2));
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            println(TextFormat.format(format, arguments));
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            println(TextFormat.format(msg, t));
        }
    }
    
    public void println(String msg){
        System.out.println(msg);
    }

}

class TextFormat {

    /**
     * 格式化字符串
     * @param message 要格式化的内容
     * @param objects 参数
     * @return
     */
    public static String format(String message, Object... objects) {
        StackTraceElement locationInfo = getTraceElement();
        StringBuilder builder=new StringBuilder();
        builder.append(new Date()).append("[class:").append(locationInfo.getClassName()).append(",method:").append(locationInfo.getMethodName()).append(",line:").append(locationInfo.getLineNumber()).append("]").append(message);
        return MessageFormat.format(replace(builder, objects), objects);
    }

    /**
     * 将msg{},{},{}替换成msg{0},{1},{2}的形式
     * @param message 要替换的内容
     * @param objects 替换对象
     * @return
     */
    private static String replace(StringBuilder message, Object... objects) {
        Integer n = 0;
        String res=message.toString();
        for (Object object : objects) {
            if (object instanceof Throwable) {
                ((Throwable) object).printStackTrace();
            } else {
                int indexOf = message.indexOf("{}");
                res= indexOf>0?message.insert(indexOf+1, n++).toString():message.toString();
            }
        }
        return res;
    }
    
    private static StackTraceElement getTraceElement(){
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if(ObjectHelper.isNotEmpty(stackTrace)){
            boolean finded=false;
            for(StackTraceElement element:stackTrace){
                if(finded) return element;
                if(LocalLogger.class.getName().equals(element.getClassName())){
                    finded=true;
                }
            }
        }
        return null;
    }
}
