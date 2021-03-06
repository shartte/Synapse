/**
 * Author: Andrei F.
 *
 * This file is part of the "Synapse" software and is licensed under
 * under the Microsoft Reference Source License (MS-RSL).
 *
 * Please see the attached LICENSE.txt for the full license.
 */

package com.af.synapse.elements;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.af.synapse.R;
import com.af.synapse.Synapse;
import com.af.synapse.utils.ActionValueClient;
import com.af.synapse.utils.ActionValueUpdater;
import com.af.synapse.utils.ActivityListener;
import com.af.synapse.utils.ElementFailureException;
import com.af.synapse.utils.Utils;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Created by Andrei on 12/09/13.
 */
public class SOptionList extends BaseElement
                         implements AdapterView.OnItemSelectedListener,
                         ActionValueClient, ActivityListener, View.OnClickListener {
    private View elementView = null;
    private Spinner spinner;
    private ImageButton previousButton;
    private ImageButton nextButton;

    private STitleBar titleObj = null;
    private SDescription descriptionObj = null;

    private String command;
    private Runnable resumeTask = null;

    List<String> items = new ArrayList<String>();
    List<String> labels = new ArrayList<String>();
    private String unit = "";

    private String original = null;
    private String stored = null;

    private String lastSelect = null;
    private String lastLive = null;

    public SOptionList(JSONObject element, LinearLayout layout) {
        super(element, layout);

        if (element.containsKey("action"))
            this.command = (String) element.get("action");
        else
            throw new IllegalArgumentException("SOptionList has no action defined");

        if (element.containsKey("unit"))
            this.unit = (String) element.get("unit");

        if (element.containsKey("default"))
            this.original = element.get("default").toString();

        if (element.containsKey("values")) {
            Object values = element.get("values");
            if (values instanceof JSONArray)
                for (Object value : (JSONArray)values) {
                    items.add(value.toString());
                    labels.add(value.toString() + unit);
                }
            else if (values instanceof JSONObject)
                for (Map.Entry<String, Object> set : ((JSONObject) values).entrySet()) {
                    items.add(set.getKey());
                    labels.add(Utils.localise(set.getValue()));
                }
        } else
            throw new IllegalArgumentException("No values given.");

        if (this.original != null && !items.contains(original))
            throw new IllegalArgumentException("Default value not contained in given values");

        /**
         *  Add a description element inside our own with the same JSON object
         */
        if (element.containsKey("description"))
            descriptionObj = new SDescription(element, layout);

        if (element.containsKey("title"))
            titleObj = new STitleBar(element, layout);

        resumeTask = new Runnable() {
            @Override
            public void run() {
                try {
                    refreshValue();
                } catch (ElementFailureException e) {
                    Utils.createElementErrorView(e);
                }
            }
        };
    }

    private void prepareUI(){
        elementView = LayoutInflater.from(Utils.mainActivity)
                            .inflate(R.layout.template_optionlist, this.layout, false);
    }

    @Override
    public View getView() throws ElementFailureException {
        if (elementView != null)
            return elementView;

        /**
         *  SOptionList needs to inflate its View inside of the main UI thread because the
         *  spinner spawns a Looper handler which may not exist in auxiliary threads.
         *
         *  We use use a CountDownLatch for inter-thread concurrency.
         */

        final CountDownLatch latch = new CountDownLatch(1);

        Utils.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                prepareUI();
                latch.countDown();
            }
        });

        String initialLive = getLiveValue();
        if (getStoredValue() == null) {
            Utils.db.setValue(command, initialLive);
            stored = lastLive;
        }
        lastSelect = lastLive;

        try { latch.await(); } catch (InterruptedException ignored) {}

        /**
         *  Nesting another element's view in our own for title and description.
         */

        LinearLayout descriptionFrame = (LinearLayout) elementView.findViewById(R.id.SOptionList_descriptionFrame);

        if (titleObj != null) {
            TextView titleView = (TextView)titleObj.getView();
            titleView.setBackground(null);
            descriptionFrame.addView(titleView);
        }

        if (descriptionObj != null)
            descriptionFrame.addView(descriptionObj.getView());

        /**
         *  Next and previous buttons
         */

        previousButton = (ImageButton)  elementView.findViewById(R.id.SOptionList_previousButton);
        nextButton = (ImageButton)  elementView.findViewById(R.id.SOptionList_nextButton);
        previousButton.setOnClickListener(this);
        nextButton.setOnClickListener(this);

        /**
         *  The spinner itself
         */

        spinner = (Spinner) elementView.findViewById(R.id.SOptionList_spinner);
        spinner.setOnItemSelectedListener(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(Utils.mainActivity,
                                                R.layout.template_optionlist_main_item, labels);
        adapter.setDropDownViewResource(R.layout.template_optionlist_list_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(items.indexOf(lastLive));

        return elementView;
    }

    private void valueCheck() {
        if (lastSelect.equals(lastLive) && lastSelect.equals(stored)) {
            elementView.setBackground(null);

            if (ActionValueUpdater.isRegistered(this))
                ActionValueUpdater.removeElement(this);
        } else {
            elementView.setBackgroundColor(Utils.mainActivity.getResources()
                    .getColor(R.color.element_value_changed));

            if (!ActionValueUpdater.isRegistered(this))
                ActionValueUpdater.registerElement(this);
        }
    }

    /**
     *  OnClickListener methods
     */

    @Override
    public void onClick(View view) {
        int i = spinner.getSelectedItemPosition();

        /* Keep in mind that the top item is of lower index */

        if (view == nextButton && i > 0)
            spinner.setSelection(i - 1);

        if (view == previousButton && i < (items.size() - 1))
            spinner.setSelection(i + 1);
    }

    /**
     *  OnItemSelectedListener methods
     */

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        ((TextView)adapterView.getChildAt(0)).setTextColor(Utils.mainActivity.getResources().getColor(android.R.color.secondary_text_light_nodisable));
        lastSelect = items.get(i);
        valueCheck();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    /**
     *  ActionValueClient methods
     */

    @Override
    public String getLiveValue() throws ElementFailureException {
        try {
            String retValue = Utils.runCommand(command);
            lastLive = retValue;
            return retValue;
        } catch (Exception e) { throw new ElementFailureException(this, e); }
    }

    @Override
    public String getSetValue() {
        return lastSelect;
    }

    @Override
    public String getStoredValue() {
        String value = Utils.db.getValue(command);
        if (value == null)
            return null;

        stored = value;
        return value;
    }

    @Override
    public void refreshValue() throws ElementFailureException {
        getLiveValue();
        if (!items.contains(lastLive)) {
            items.add(lastLive);
            labels.add(lastLive + unit);
        }

        if (lastSelect.equals(lastLive))
            return;

        final int selection = items.indexOf(lastLive);
        lastSelect = lastLive;

        Utils.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setSelection(selection);
                valueCheck();
            }
        });
    }

    @Override
    public void setDefaults() {
        if (original != null) {
            spinner.setSelection(items.indexOf(original));
            valueCheck();
        }
    }

    @Override
    public boolean commitValue() throws ElementFailureException {
        try {
            Utils.runCommand(command + " " + lastSelect);
        } catch (Exception e) { throw new ElementFailureException(this, e); }

        getLiveValue();

        if (!lastLive.equals(stored))
            Utils.db.setValue(command, lastLive);

        lastSelect = stored = lastLive;
        int selection = items.indexOf(lastLive);
        spinner.setSelection(selection);
        valueCheck();

        return true;
    }

    @Override
    public void cancelValue() throws ElementFailureException {
        lastSelect = lastLive = stored;
        commitValue();
    }
    /**
     *  ActivityListener methods
     */

    @Override
    public void onStart() throws ElementFailureException {}

    @Override
    public void onResume() {
        if (!Utils.mainActivity.isChangingConfigurations() && Utils.appStarted)
            Synapse.executor.execute(resumeTask);
    }

    @Override
    public void onPause() {}

    @Override
    public void onStop() {}

}
