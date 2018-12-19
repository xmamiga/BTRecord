package com.xxhander;

import android.os.Message;

/**
 * handler回调更新接口（核心）
 *
 */
public interface BaseHandlerUpDate {
	/**
	 * handler回调接口
	 * @param msg
	 */
	public void handleMessage(Message msg);
}
