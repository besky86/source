package com.lei.utils;

import android.app.Activity;
import android.view.WindowManager;


public class Tools {

	public static void setInputMode(Activity context){
        int mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
            mode |= WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;

        context.getWindow().setSoftInputMode(mode);
	}
}
