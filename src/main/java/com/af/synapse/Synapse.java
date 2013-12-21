/**
 * Author: Andrei F.
 *
 * This file is part of the "Synapse" software and is licensed under
 * under the Microsoft Reference Source License (MS-RSL).
 *
 * Please see the attached LICENSE.txt for the full license.
 */

package com.af.synapse;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import com.af.synapse.utils.RootFailureException;
import com.af.synapse.utils.Utils;

/**
 * Created by Andrei on 04/10/13.
 */
public class Synapse extends Application {
    private static Context context;
    public static Handler handler;

    public static boolean isValidEnvironment = false;

    public void onCreate(){
        super.onCreate();

        Synapse.context = getApplicationContext();
        Synapse.handler = new Handler();
        Utils.initiateDatabase();

        assert context.getResources().getConfiguration().locale != null;
        Utils.locale = context.getResources().getConfiguration().locale.toString();

        try { isValidEnvironment = Utils.isUciSupport(); } catch (RootFailureException ignored) {}

        if (isValidEnvironment)
            Utils.loadSections();
    }

    public static Context getAppContext() {
        return Synapse.context;
    }

    @Override
    public void onTerminate(){
        super.onTerminate();

        Utils.destroy();
    }
}