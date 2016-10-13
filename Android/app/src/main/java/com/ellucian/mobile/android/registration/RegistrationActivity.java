/*
 * Copyright 2015-2016 Ellucian Company L.P. and its affiliates.
 */

package com.ellucian.mobile.android.registration;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TabLayout.Tab;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.ellucian.elluciango.R;
import com.ellucian.mobile.android.adapter.CheckableCursorAdapter;
import com.ellucian.mobile.android.adapter.CheckableCursorAdapter.OnCheckBoxClickedListener;
import com.ellucian.mobile.android.adapter.CheckableSectionedListAdapter;
import com.ellucian.mobile.android.adapter.ModuleMenuAdapter;
import com.ellucian.mobile.android.app.EllucianActivity;
import com.ellucian.mobile.android.app.EllucianDefaultListFragment;
import com.ellucian.mobile.android.app.GoogleAnalyticsConstants;
import com.ellucian.mobile.android.client.MobileClient;
import com.ellucian.mobile.android.client.courses.Instructor;
import com.ellucian.mobile.android.client.courses.MeetingPattern;
import com.ellucian.mobile.android.client.registration.CartResponse;
import com.ellucian.mobile.android.client.registration.EligibilityResponse;
import com.ellucian.mobile.android.client.registration.EligibleTerm;
import com.ellucian.mobile.android.client.registration.Message;
import com.ellucian.mobile.android.client.registration.OpenTerm;
import com.ellucian.mobile.android.client.registration.Plan;
import com.ellucian.mobile.android.client.registration.RegisterSection;
import com.ellucian.mobile.android.client.registration.RegistrationResponse;
import com.ellucian.mobile.android.client.registration.SearchResponse;
import com.ellucian.mobile.android.client.registration.Section;
import com.ellucian.mobile.android.client.registration.Term;
import com.ellucian.mobile.android.client.registration.UpdateResponse;
import com.ellucian.mobile.android.client.services.RegisterService;
import com.ellucian.mobile.android.client.services.RegistrationCartUpdateService;
import com.ellucian.mobile.android.registration.RefineSearchDialogFragment.OnDoneFilteringListener;
import com.ellucian.mobile.android.util.CalendarUtils;
import com.ellucian.mobile.android.util.Extra;
import com.ellucian.mobile.android.util.Utils;
import com.ellucian.mobile.android.util.VersionSupportUtils;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class RegistrationActivity extends EllucianActivity implements OnDoneFilteringListener {
    private static final String TAG = RegistrationActivity.class.getSimpleName();

    private final Activity activity = this;
    private final int CART_TAB_INDEX = 0;
    private final int SEARCH_TAB_INDEX = 1;
    private final int REGISTERED_TAB_INDEX = 2;

    static final int REGISTRATION_DETAIL_REQUEST_CODE = 8888;
    static final int RESULT_REMOVE = 9999;

    private static final String PLAN_ID = "planId";
    public static final String TERM_ID = "termId";
    static final String SECTION_ID = "sectionId";
    static final String SECTION = "section";
    private static final String TERM_NAME = "termName";
    private static final String CART_IDENTIFIER = "Cart";
    private static final String REGISTERED_IDENTIFIER = "Registered";
    private static final String ACTION_REGISTER = "actionRegister";
    private static final String ACTION_DROP = "actionDrop";

    private RegistrationCartListAdapter cartAdapter;
    private RegistrationCartListFragment cartFragment;
    private RegistrationSearchFragment searchFragment;
    private RegistrationResultsFragment registerResultsFragment;
    private RegistrationSearchResultsListFragment searchResultsFragment;
    private RegistrationRegisteredListFragment registeredFragment;
    private CartResponse currentCart;
    private RetrieveCartListTask cartListTask;
    private int previousSelected;
    private boolean eligibilityChecked;
    private boolean planPresent;
    private CheckEligibilityTask eligibilityTask;
    private RegisterReceiver registerReceiver;
    private DropReceiver dropReceiver;
    private CartUpdateReceiver cartUpdateReceiver;
    private long startTime;
    private TabLayout tabLayout;
    private int currentTab;

    private SearchSectionTask searchTask;
    private SearchResponse currentResults;
    private CheckableSectionedListAdapter resultsAdapter;
    private CheckableSectionedListAdapter registeredAdapter;

    OpenTerm[] openTerms;
    private HashMap<String, String> termPinMap;

    private SimpleDateFormat defaultTimeParserFormat;
    private SimpleDateFormat altTimeParserFormat;
    private DateFormat timeFormatter;

    private Gson gson;

    private boolean isInForeground;
    static final String AUTH_CODE_REQUIRED = "AUTH_CODE_REQUIRED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_footer_dual_pane);

        setTitle(moduleName);

        isInForeground = true;

        defaultTimeParserFormat = new SimpleDateFormat("HH:mm'Z'", Locale.US);
        defaultTimeParserFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        altTimeParserFormat = new SimpleDateFormat("HH:mm", Locale.US);
        timeFormatter = android.text.format.DateFormat.getTimeFormat(this);

        gson = new Gson();


        FragmentManager manager = getSupportFragmentManager();
        cartFragment = (RegistrationCartListFragment) manager.findFragmentByTag("RegistrationCartListFragment");

        if (cartFragment == null) {
            cartFragment = (RegistrationCartListFragment) EllucianDefaultListFragment.newInstance(this, RegistrationCartListFragment.class.getName(), null);
        }

        searchFragment = (RegistrationSearchFragment) manager.findFragmentByTag("RegistrationSearchFragment");

        if (searchFragment == null) {
            searchFragment = (RegistrationSearchFragment) Fragment.instantiate(this, RegistrationSearchFragment.class.getName());
        }

        registeredFragment = (RegistrationRegisteredListFragment) manager.findFragmentByTag("RegistrationRegisteredListFragment");

        if (registeredFragment == null) {
            registeredFragment = (RegistrationRegisteredListFragment) Fragment.instantiate(this, RegistrationRegisteredListFragment.class.getName());
        }

        cartAdapter = new RegistrationCartListAdapter(this, R.layout.registration_auth_required_header, AUTH_CODE_REQUIRED);
        resultsAdapter = new CheckableSectionedListAdapter(this);
        registeredAdapter = new CheckableSectionedListAdapter(this);

        // Setup the 3 tabs for Cart, Search, Registered in 1 TabLayout.
        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setVisibility(View.VISIBLE);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabLayout.setSelectedTabIndicatorColor(VersionSupportUtils.getColorHelper(this, R.color.tab_indicator_color));


        Tab cartTab = tabLayout.newTab().setText(getCurrentCartText());
        Tab searchTab = tabLayout.newTab().setText(R.string.registration_tab_search);
        Tab registeredTab = tabLayout.newTab().setText(R.string.registration_tab_registered);
        tabLayout.addTab(cartTab, CART_TAB_INDEX);
        tabLayout.addTab(searchTab, SEARCH_TAB_INDEX);
        tabLayout.addTab(registeredTab, REGISTERED_TAB_INDEX);
        tabLayout.addOnTabSelectedListener(new RegistrationTabListener(this, R.id.frame_main));

        boolean registerResultsAdded = false;
        boolean searchResultsAdded = false;

        if (savedInstanceState != null) {
            eligibilityChecked = savedInstanceState.getBoolean("eligibilityChecked");

            if (savedInstanceState.containsKey("currentCart")) {
                Log.d(TAG, "Found saved cart, restoring.");
                currentCart = savedInstanceState.getParcelable("currentCart");
            }
            if (savedInstanceState.containsKey("currentResults")) {
                Log.d(TAG, "Found saved currentResults, restoring.");
                currentResults = savedInstanceState.getParcelable("currentResults");
            }
            if (savedInstanceState.containsKey("previousSelected")) {
                previousSelected = savedInstanceState.getInt("previousSelected");
            }
            if (savedInstanceState.containsKey("registerResultsAdded")) {
                registerResultsAdded = savedInstanceState.getBoolean("registerResultsAdded");
            }
            if (savedInstanceState.containsKey("searchResultsAdded")) {
                searchResultsAdded = savedInstanceState.getBoolean("searchResultsAdded");
            }
            if (savedInstanceState.containsKey("termPinMap")) {
                termPinMap = (HashMap<String, String>) savedInstanceState.getSerializable("termPinMap");
            }
        }

        if (currentCart != null) {
            planPresent = true;
            Log.d(TAG, "Cart is current, building adapter.");

            fillCartAdapter(currentCart, savedInstanceState);
            fillRegisteredAdapter(currentCart, savedInstanceState);
            if (cartAdapter == null) {
                Log.e(TAG, "cartAdapter is null");
            }

        }

        if (currentResults != null) {
            Log.d(TAG, "Results is current, building adapter.");

            boolean addAuthCodeWarning = false;
            for (Section section : currentResults.sections) {
                if (section.authorizationCodeRequired) {
                    addAuthCodeWarning = true;
                }
            }

            if (addAuthCodeWarning) {
                resultsAdapter = new CheckableSectionedListAdapter(this, R.layout.registration_search_results_header);
            }

            fillSearchResultsAdapter(currentResults, savedInstanceState, addAuthCodeWarning);
            if (resultsAdapter == null) {
                Log.e(TAG, "resultsAdapter is null");
            }

        }

        cartFragment.setListAdapter(cartAdapter);
        registeredFragment.setListAdapter(registeredAdapter);

        if (!eligibilityChecked) {
            eligibilityTask = new CheckEligibilityTask();
            eligibilityTask.execute(requestUrl);
        }

        clearMainFragment();
        if (previousSelected == SEARCH_TAB_INDEX) {
            cartTab.setTag(true);
            searchTab.setTag(true);
            registeredTab.setTag(true);
            if (searchResultsAdded) {
                FragmentTransaction ft = manager.beginTransaction();
                clearMainFragment();

                // Set the Search tab as selected on rotate
                LinearLayout ll = (LinearLayout) tabLayout.getChildAt(0);
                ll.getChildAt(tabLayout.getSelectedTabPosition()).setSelected(false);
                ll.getChildAt(SEARCH_TAB_INDEX).setSelected(true);
                tabLayout.setScrollPosition(SEARCH_TAB_INDEX, 0, true);

                searchResultsFragment = (RegistrationSearchResultsListFragment) manager.findFragmentByTag("RegistrationSearchResultsListFragment");
                searchResultsFragment.setListAdapter(resultsAdapter);
                ft.attach(searchResultsFragment);
                ft.commit();
            } else {
                tabLayout.getTabAt(SEARCH_TAB_INDEX).select();
            }

        } else if (previousSelected == REGISTERED_TAB_INDEX) {
            cartTab.setTag(true);
            searchTab.setTag(true);
            registeredTab.setTag(true);
            tabLayout.getTabAt(REGISTERED_TAB_INDEX).select();
            if (registerResultsAdded) {
                FragmentTransaction ft = manager.beginTransaction();
                clearMainFragment();

                registerResultsFragment = (RegistrationResultsFragment) manager.findFragmentByTag("RegistrationResultsFragment");

                ft.attach(registerResultsFragment);
                ft.commit();
            }

        } else {
            if (currentCart != null) {
                cartTab.setTag(true);
                searchTab.setTag(true);
                registeredTab.setTag(true);
                if (registerResultsAdded) {
                    FragmentTransaction ft = manager.beginTransaction();
                    clearMainFragment();

                    registerResultsFragment = (RegistrationResultsFragment) manager.findFragmentByTag("RegistrationResultsFragment");

                    ft.attach(registerResultsFragment);
                    ft.commit();
                } else {
                    tabLayout.getTabAt(CART_TAB_INDEX).select();
                }

            } else {
                cartTab.setTag(false);
                searchTab.setTag(false);
                registeredTab.setTag(false);

                Log.d(TAG, "No cart found, retrieving.");
                Utils.showProgressIndicator(activity);
                cartListTask = new RetrieveCartListTask();
                cartListTask.execute(requestUrl);

            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
        // Check if the RegisterService is still currently running and if so show the progress bar
        if (getEllucianApp().isServiceRunning(RegisterService.class)) {
            Utils.showProgressIndicator(activity);
        }
        registerReceiver = new RegisterReceiver();
        cartUpdateReceiver = new CartUpdateReceiver();
        dropReceiver = new DropReceiver();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(registerReceiver, new IntentFilter(RegisterService.ACTION_REGISTER_FINISHED));
        lbm.registerReceiver(dropReceiver, new IntentFilter(RegisterService.ACTION_DROP_FINISHED));
        lbm.registerReceiver(cartUpdateReceiver, new IntentFilter(RegistrationCartUpdateService.ACTION_UPDATE_FINISHED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(registerReceiver);
        lbm.unregisterReceiver(dropReceiver);
        lbm.unregisterReceiver(cartUpdateReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (currentCart != null) {
            outState.putParcelable("currentCart", currentCart);

            if (cartAdapter != null) {
                for (int i = 0; i < cartAdapter.headers.getCount(); i++) {
                    String adapterSectionIdentifier = cartAdapter.identifiers.get(i);
                    CheckableCursorAdapter cursorAdapter = (CheckableCursorAdapter) cartAdapter.sections.get(i);
                    outState.putBooleanArray(CART_IDENTIFIER + "-" + adapterSectionIdentifier, cursorAdapter.getCheckedStatesAsBooleanArray());

                }
            }
            if (registeredAdapter != null) {
                for (int i = 0; i < registeredAdapter.headers.getCount(); i++) {
                    String adapterSectionIdentifier = registeredAdapter.identifiers.get(i);
                    CheckableCursorAdapter cursorAdapter = (CheckableCursorAdapter) registeredAdapter.sections.get(i);
                    outState.putBooleanArray(REGISTERED_IDENTIFIER + "-" + adapterSectionIdentifier, cursorAdapter.getCheckedStatesAsBooleanArray());

                }
            }
        }

        if (currentResults != null) {
            outState.putParcelable("currentResults", currentResults);

            if (resultsAdapter != null) {
                for (int i = 0; i < resultsAdapter.headers.getCount(); i++) {
                    String headerName = resultsAdapter.headers.getItem(i);
                    CheckableCursorAdapter cursorAdapter = (CheckableCursorAdapter) resultsAdapter.sections.get(i);
                    outState.putBooleanArray(headerName, cursorAdapter.getCheckedStatesAsBooleanArray());
                }
            }
        }

        int mCurrentTab = currentTab;

        if (registerResultsFragment != null) {
            if (registerResultsFragment.isAdded()) {
                outState.putBoolean("registerResultsAdded", true);
            }
        }

        if (searchResultsFragment != null) {
            if (searchResultsFragment.isAdded()) {
                outState.putBoolean("searchResultsAdded", true);
                mCurrentTab = SEARCH_TAB_INDEX;
            }
        }

        if (termPinMap != null) {
            outState.putSerializable("termPinMap", termPinMap);
        }

        outState.putInt("previousSelected", mCurrentTab);
        outState.putBoolean("eligibilityChecked", eligibilityChecked);
    }

    @Override
    protected void onDestroy() {
        if (eligibilityTask != null && eligibilityTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.e(TAG, "Cancelling eligibility task");
            if (eligibilityTask.cancel(true)) {
                Log.e(TAG, "Cancelled");
            } else {
                Log.e(TAG, "failed to cancel");
            }
        }
        if (searchTask != null && searchTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.e(TAG, "Cancelling search task");
            if (searchTask.cancel(true)) {
                Log.e(TAG, "Cancelled");
            } else {
                Log.e(TAG, "failed to cancel");
            }
        }
        if (cartListTask != null && cartListTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.e(TAG, "Cancelling cart task");
            if (cartListTask.cancel(true)) {
                Log.e(TAG, "Cancelled");
            } else {
                Log.e(TAG, "failed to cancel");
            }
        }
        super.onDestroy();
    }

    // If in single-pane mode this will be called from the NotificationDetailActivity to signal a delete
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REGISTRATION_DETAIL_REQUEST_CODE && resultCode == RESULT_REMOVE) {
            removeItemFromCart();
        }
    }

    // Get the updated text for the Cart Tab showing the number of items currently in the cart
    private String getCurrentCartText() {
        if (cartAdapter != null && cartAdapter.getCountWithoutHeaders() > 0) {
            return getString(R.string.label_with_count_format,
                    getString(R.string.registration_tab_cart),
                    cartAdapter.getCountWithoutHeaders());
        } else {
            return getString(R.string.registration_tab_cart);
        }

    }

    private void clearMainFragment() {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction ft = manager.beginTransaction();
        Fragment mainFrame = manager.findFragmentById(R.id.frame_main);

        if (mainFrame != null) {
            ft.detach(mainFrame);
        }
        ft.commitAllowingStateLoss();
    }

    private void clearDetailFragment() {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction ft = manager.beginTransaction();
        Fragment extraFrame = manager.findFragmentById(R.id.frame_extra);

        if (extraFrame != null) {
            ft.detach(extraFrame);
        }

        ft.commitAllowingStateLoss();
    }

    private void enableTabs() {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            tabLayout.getTabAt(i).setTag(true);
        }
    }

    private void fillCartAdapter(CartResponse response, Bundle savedInstanceState) {
        //  First time through, display courses that require authorization
        splitCartAdapter(true, response, savedInstanceState);

        //  Second time through, display courses that DO NOT require authorization
        splitCartAdapter(false, response, savedInstanceState);

        // After adapter is filled update the number of items in cart showing in tab
        if (tabLayout.getTabCount() > 0) {
            Tab cartTab = tabLayout.getTabAt(CART_TAB_INDEX);
            if (cartTab != null) {
                cartTab.setText(getCurrentCartText());
            } else {
                Log.d(TAG, "cartTab is null");
            }
        } else {
            Log.d(TAG, "Current tab count is 0");
        }

    }

    private void splitCartAdapter(boolean sectionsWithAuthCodeRequired, CartResponse response, Bundle savedInstanceState) {
        if (response.plans != null) {
            for (Plan plan : response.plans) {
                for (Term term : plan.terms) {

                    if (term.plannedCourses.length > 0) {

                        MatrixCursor sectionedCursor = new MatrixCursor(new String[]{
                                BaseColumns._ID, SECTION_ID, TERM_ID, TERM_NAME, PLAN_ID
                        });

                        int row = 1;
                        for (Section course : term.plannedCourses) {
                            if (!sectionsWithAuthCodeRequired == course.authorizationCodeRequired) {
                                continue;
                            }

                            // only showing non-registered sections
                            if (!TextUtils.isEmpty(course.classification) && !course.classification.equals(Section.CLASSIFICATION_REGISTERED)) {
                                Log.d(TAG, course.courseName + " is not registered, showing in cart");
                                sectionedCursor.addRow(new Object[]{"" + row++,
                                        course.sectionId, term.termId, term.name, plan.planId
                                });
                            } else {
                                Log.d(TAG, course.courseName + " is already registered");
                            }
                        }

                        CheckableCursorAdapter cursorAdapter;
                        if (sectionsWithAuthCodeRequired) {
                            cursorAdapter = new RegistrationCartCheckableAdapter(
                                    this,
                                    R.layout.registration_list_checkbox_row,
                                    sectionedCursor,
                                    new String[]{SECTION_ID},
                                    new int[]{R.id.registration_row_layout},
                                    0,
                                    R.id.checkbox);

                            cursorAdapter.setViewBinder(new RegistrationViewBinder());
                            ((RegistrationCartCheckableAdapter) cursorAdapter).registerOnCartCheckBoxClickedListener(new RegisterAuthCodeCheckBoxClickedListener());

                        } else {
                            cursorAdapter = new CheckableCursorAdapter(
                                    this,
                                    R.layout.registration_list_checkbox_row,
                                    sectionedCursor,
                                    new String[]{SECTION_ID},
                                    new int[]{R.id.registration_row_layout},
                                    0,
                                    R.id.checkbox);

                            cursorAdapter.setViewBinder(new RegistrationViewBinder());
                            cursorAdapter.registerOnCheckBoxClickedListener(new RegisterCheckBoxClickedListener());
                        }

                        if (savedInstanceState != null) {
                            boolean[] checkedStatesBooleanArray = savedInstanceState.getBooleanArray(CART_IDENTIFIER + "-" + term.termId);
                            if (checkedStatesBooleanArray != null) {
                                cursorAdapter.setCheckedStates(checkedStatesBooleanArray);
                            }
                        }
                        if (sectionedCursor.getCount() > 0) {
                            if (sectionsWithAuthCodeRequired) {
                                cartAdapter.addSection(term.name, AUTH_CODE_REQUIRED, cursorAdapter);
                            } else {
                                cartAdapter.addSection(term.name, term.termId, cursorAdapter);
                            }

                        }
                    }
                }
            }
        }
    }

    private void fillRegisteredAdapter(CartResponse response, Bundle savedInstanceState) {

        for (Plan plan : response.plans) {
            for (Term term : plan.terms) {

                if (term.plannedCourses.length > 0) {

                    MatrixCursor sectionedCursor = new MatrixCursor(new String[]{
                            BaseColumns._ID, SECTION_ID, TERM_ID, TERM_NAME, PLAN_ID
                    });

                    int row = 1;
                    for (Section course : term.plannedCourses) {

                        if (!TextUtils.isEmpty(course.classification) && course.classification.equals(Section.CLASSIFICATION_REGISTERED)) {
                            Log.d(TAG, course.courseName + " is registered, showing in cart");
                            sectionedCursor.addRow(new Object[]{"" + row++,
                                    course.sectionId, term.termId, term.name, plan.planId
                            });
                        } else {
                            Log.d(TAG, course.courseName + " is not registered");
                        }

                    }

                    CheckableCursorAdapter cursorAdapter = new CheckableCursorAdapter(
                            this,
                            R.layout.registration_list_checkbox_row,
                            sectionedCursor,
                            new String[]{SECTION_ID},
                            new int[]{R.id.registration_row_layout},
                            0,
                            R.id.checkbox);

                    cursorAdapter.setViewBinder(new RegistrationViewBinder());
                    cursorAdapter.registerOnCheckBoxClickedListener(new DropCheckBoxClickedListener());
                    if (savedInstanceState != null) {
                        boolean[] checkedStatesBooleanArray = savedInstanceState.getBooleanArray(REGISTERED_IDENTIFIER + "-" + term.termId);
                        if (checkedStatesBooleanArray != null) {
                            cursorAdapter.setCheckedStates(checkedStatesBooleanArray);
                        }
                    }
                    if (sectionedCursor.getCount() > 0) {
                        registeredAdapter.addSection(term.name, term.termId, cursorAdapter);
                    }
                }
            }
        }

    }

    Section findSectionInCart(String termId, String sectionId) {
        if (currentCart != null) {
            for (Plan plan : currentCart.plans) {
                for (Term term : plan.terms) {
                    if (term.termId.equals(termId)) {
                        for (Section course : term.plannedCourses) {
                            if (course.sectionId.equals(sectionId)) {
                                return course;
                            }
                        }
                    }
                }
            }

            Log.e(TAG, "cannot find course in currentCart");
            return null;
        } else {
            Log.e(TAG, "currentCart is null, cannot find course");
            return null;
        }
    }

    public void onRegisterClicked(View view) {
        if (planPresent && !getEllucianApp().isServiceRunning(RegisterService.class)) {
            RegisterConfirmDialogFragment registerConfirmDialogFragment = new RegisterConfirmDialogFragment();
            registerConfirmDialogFragment.show(getSupportFragmentManager(), "RegisterConfirmDialogFragment");
        }
    }

    void onRegisterConfirmOkClicked() {

        List<TermInfoHolder> termsThatNeedPins = getTermsThatNeedPins(ACTION_REGISTER);
        if (termsThatNeedPins != null && !termsThatNeedPins.isEmpty()) {
            Log.d(TAG, "Pins required for register.");
            PinConfirmDialogFragment pinDialogFragment = new PinConfirmDialogFragment();
            pinDialogFragment.termsThatNeedPins = termsThatNeedPins;
            pinDialogFragment.action = ACTION_REGISTER;
            pinDialogFragment.setCancelable(false);
            pinDialogFragment.show(getSupportFragmentManager(), "PinDialogFragment");

        } else {
            Log.d(TAG, "No pins required, continuing register");
            finishRegister();
        }
    }

    private void setupTermPinMap(EligibleTerm[] terms) {
        Log.d(TAG, "setting up termPinMap");
        termPinMap = new HashMap<>();

        for (EligibleTerm term : terms) {
            if (term.requireAltPin) {
                Log.d(TAG, "AltPin required for term: " + term.term);
                termPinMap.put(term.term, "");
            }
        }
    }

    private void clearTermPins() {
        Log.d(TAG, "clearing term pins");
        if (termPinMap != null) {
            for (String termId : termPinMap.keySet()) {
                termPinMap.put(termId, "");
            }
        }
    }

    void onPinConfirmOkClicked(HashMap<String, String> pinMap, String action) {
        termPinMap.putAll(pinMap);
        if (!TextUtils.isEmpty(action) && action.equals(ACTION_DROP)) {
            finishDrop();
        } else {
            finishRegister();
        }

    }

    private List<TermInfoHolder> getTermsThatNeedPins(String action) {
        Log.d(TAG, "Looking for selected terms that require a pin for action: " + action);
        CheckableSectionedListAdapter adapter;
        if (!TextUtils.isEmpty(action) && action.equals(ACTION_DROP)) {
            adapter = (CheckableSectionedListAdapter) registeredFragment.getListAdapter();
        } else {
            adapter = (CheckableSectionedListAdapter) cartFragment.getListAdapter();
        }

        List<TermInfoHolder> termListToCheck = new ArrayList<>();
        List<TermInfoHolder> termsThatNeedPins = new ArrayList<>();

        Cursor cursor;
        for (int checkedPosition : adapter.getCheckedPositions()) {
            cursor = (Cursor) adapter.getItem(checkedPosition);
            String termId = cursor.getString(cursor.getColumnIndex(TERM_ID));
            String termName = cursor.getString(cursor.getColumnIndex(TERM_NAME));
            cursor.close();
            TermInfoHolder holder = new TermInfoHolder();
            holder.termId = termId;
            holder.termName = termName;
            termListToCheck.add(holder);
        }

        if (termPinMap != null) {
            for (TermInfoHolder holder : termListToCheck) {
                if (termPinMap.containsKey(holder.termId)) {
                    String termPin = termPinMap.get(holder.termId);
                    if (TextUtils.isEmpty(termPin)) {
                        if (!termsThatNeedPins.contains(holder)) {
                            termsThatNeedPins.add(holder);
                        }

                    }
                }
            }
        }

        return termsThatNeedPins;

    }

    private void finishRegister() {
        sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Register", null, moduleName);
        List<PlanToRegister> plansToRegister = getPlansToRegister();
        if (plansToRegister != null && !plansToRegister.isEmpty()) {

            PlanToRegister plan = plansToRegister.get(0);
            String planInJson = gson.toJson(plan);
            Log.d(TAG, "Registering : " + planInJson);

            Intent registerIntent = new Intent(this, RegisterService.class);
            registerIntent.putExtra(Extra.REQUEST_URL, requestUrl);
            registerIntent.putExtra(ModuleMenuAdapter.PLANNING_TOOL,
                    getIntent().getBooleanExtra(ModuleMenuAdapter.PLANNING_TOOL, false));
            registerIntent.putExtra(RegisterService.PLAN_TO_REGISTER, planInJson);
            startTime = System.currentTimeMillis();
            startService(registerIntent);
            Utils.showProgressIndicator(activity);
        } else {
            Log.e(TAG, "List of plans is either null or empty");
        }
    }

    private List<PlanToRegister> getPlansToRegister() {
        HashMap<String, List<SectionRegistration>> selectionMap = new HashMap<>();
        CheckableSectionedListAdapter adapter = (CheckableSectionedListAdapter) cartFragment.getListAdapter();

        List<SectionRegistration> currentSelectionList;
        for (int checkedPosition : adapter.getCheckedPositions()) {
            Cursor cursor = (Cursor) adapter.getItem(checkedPosition);

            String sectionId = cursor.getString(cursor.getColumnIndex(SECTION_ID));
            String termId = cursor.getString(cursor.getColumnIndex(TERM_ID));
            String planId = cursor.getString(cursor.getColumnIndex(PLAN_ID));

            cursor.close();

            if (selectionMap.containsKey(planId)) {
                currentSelectionList = selectionMap.get(planId);
            } else {
                currentSelectionList = new ArrayList<>();
                selectionMap.put(planId, currentSelectionList);
            }

            Section section = findSectionInCart(termId, sectionId);
            String action;
            Float credits = null;
            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                action = Section.GRADING_TYPE_AUDIT;
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                action = Section.GRADING_TYPE_PASS_FAIL;
            } else {
                action = "Add";
                if (section.selectedCredits != -1) {
                    credits = section.selectedCredits;
                } else if (section.minimumCredits != 0 && section.maximumCredits != 0) {
                    credits = section.credits;
                }
            }

            SectionRegistration sectionRegistration = new SectionRegistration();
            sectionRegistration.termId = termId;
            sectionRegistration.sectionId = sectionId;
            sectionRegistration.action = action;
            sectionRegistration.credits = credits;

            if (termPinMap != null && termPinMap.containsKey(termId)) {
                sectionRegistration.altPin = termPinMap.get(termId);
            }

            if (section.authorizationCodeRequired) {
                sectionRegistration.authorizationCode = section.authorizationCodePresented;
            }

            currentSelectionList.add(sectionRegistration);
        }

        List<PlanToRegister> plansToRegister = new ArrayList<>();
        for (String planId : selectionMap.keySet()) {
            PlanToRegister newPlan = new PlanToRegister();
            newPlan.planId = planId;
            ArrayList<SectionRegistration> listToConvert = (ArrayList<SectionRegistration>) selectionMap.get(planId);
            newPlan.sectionRegistrations = listToConvert.toArray(new SectionRegistration[listToConvert.size()]);
            plansToRegister.add(newPlan);
        }

        return plansToRegister;

    }

    private void updateSections(RegisterSection[] registeredSections, String sectionClassification) {
        int updated = 0;
        for (RegisterSection registeredSection : registeredSections) {
            Section section = findSectionInCart(registeredSection.termId, registeredSection.sectionId);
            if (section != null) {
                Log.d(TAG, "Updating " + section.courseName + "-" + section.courseSectionNumber + " to " + sectionClassification);
                section.classification = sectionClassification;
                updated++;
            }
        }

        if (updated > 0) {
            cartAdapter = new RegistrationCartListAdapter(this, R.layout.registration_auth_required_header, AUTH_CODE_REQUIRED);
            registeredAdapter = new CheckableSectionedListAdapter(this);
            cartFragment.setListAdapter(cartAdapter);
            registeredFragment.setListAdapter(registeredAdapter);
            fillCartAdapter(currentCart, null);
            fillRegisteredAdapter(currentCart, null);
        }
    }

    public void onDropClicked(View view) {
        if (planPresent && !getEllucianApp().isServiceRunning(RegisterService.class)) {
            DropConfirmDialogFragment dropConfirmDialogFragment = new DropConfirmDialogFragment();
            dropConfirmDialogFragment.show(getSupportFragmentManager(), "DropConfirmDialogFragment");
        }
    }

    void onDropConfirmOkClicked() {
        List<TermInfoHolder> termsThatNeedPins = getTermsThatNeedPins(ACTION_DROP);
        if (termsThatNeedPins != null && !termsThatNeedPins.isEmpty()) {
            Log.d(TAG, "Pins required for drop");
            PinConfirmDialogFragment pinDialogFragment = new PinConfirmDialogFragment();
            pinDialogFragment.termsThatNeedPins = termsThatNeedPins;
            pinDialogFragment.action = ACTION_DROP;
            pinDialogFragment.setCancelable(false);
            pinDialogFragment.show(getSupportFragmentManager(), "PinDialogFragment");

        } else {
            Log.d(TAG, "No pins required, continuing drop");
            finishDrop();
        }

    }

    private void finishDrop() {
        sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Drop", null, moduleName);
        List<PlanToRegister> plansToDrop = getPlansToDrop();
        if (plansToDrop != null && !plansToDrop.isEmpty()) {

            PlanToRegister plan = plansToDrop.get(0);
            String planInJson = gson.toJson(plan);
            Log.d(TAG, "Dropping : " + planInJson);

            Intent registerIntent = new Intent(this, RegisterService.class);
            registerIntent.putExtra(Extra.REQUEST_URL, requestUrl);
            registerIntent.putExtra(ModuleMenuAdapter.PLANNING_TOOL,
                    getIntent().getBooleanExtra(ModuleMenuAdapter.PLANNING_TOOL, false));
            registerIntent.putExtra(RegisterService.PLAN_TO_REGISTER, planInJson);
            registerIntent.putExtra(RegisterService.REGISTER_TYPE, RegisterService.TYPE_DROP);
            startService(registerIntent);
            Utils.showProgressIndicator(activity);
        } else {
            Log.e(TAG, "List of plans is either null or empty");
        }
    }

    private List<PlanToRegister> getPlansToDrop() {
        HashMap<String, List<SectionRegistration>> selectionMap = new HashMap<>();
        CheckableSectionedListAdapter adapter = (CheckableSectionedListAdapter) registeredFragment.getListAdapter();

        List<SectionRegistration> currentSelectionList;
        for (int checkedPosition : adapter.getCheckedPositions()) {
            Cursor cursor = (Cursor) adapter.getItem(checkedPosition);

            String sectionId = cursor.getString(cursor.getColumnIndex(SECTION_ID));
            String termId = cursor.getString(cursor.getColumnIndex(TERM_ID));
            String planId = cursor.getString(cursor.getColumnIndex(PLAN_ID));

            cursor.close();

            if (selectionMap.containsKey(planId)) {
                currentSelectionList = selectionMap.get(planId);
            } else {
                currentSelectionList = new ArrayList<>();
                selectionMap.put(planId, currentSelectionList);
            }

            Section section = findSectionInCart(termId, sectionId);
            String action;
            Float credits = null;

            action = "Drop";
            if (section.selectedCredits != -1) {
                credits = section.selectedCredits;
            } else if (section.minimumCredits != 0 && section.maximumCredits != 0) {
                credits = section.credits;
            }

            SectionRegistration sectionRegistration = new SectionRegistration();
            sectionRegistration.termId = termId;
            sectionRegistration.sectionId = sectionId;
            sectionRegistration.action = action;
            sectionRegistration.credits = credits;

            if (termPinMap != null && termPinMap.containsKey(termId)) {
                sectionRegistration.altPin = termPinMap.get(termId);
            }

            currentSelectionList.add(sectionRegistration);
        }

        List<PlanToRegister> plansToDrop = new ArrayList<>();
        for (String planId : selectionMap.keySet()) {
            PlanToRegister newPlan = new PlanToRegister();
            newPlan.planId = planId;
            ArrayList<SectionRegistration> listToConvert = (ArrayList<SectionRegistration>) selectionMap.get(planId);
            newPlan.sectionRegistrations = listToConvert.toArray(new SectionRegistration[listToConvert.size()]);
            plansToDrop.add(newPlan);
        }

        return plansToDrop;

    }

    void onVariableCreditsConfirmOkClicked(String termId, String sectionId, float credits) {
        Section section = findSectionInResults(termId, sectionId);
        float setCredits = section.selectedCredits;
        section.selectedCredits = credits;

        if (setCredits != section.selectedCredits) {
            resultsAdapter.notifyDataSetChanged();
        }
    }

    void onVariableCreditsConfirmCancelClicked(int position) {
        CheckBox checkBox = resultsAdapter.getCheckBoxAtPosition(position);
        if (checkBox != null) {
            checkBox.performClick();
        }
    }

    void onAuthCodeConfirmOkClicked(String termId, String sectionId, String authCodeEntered) {
        Section section = findSectionInCart(termId, sectionId);
        String authCode = section.authorizationCodePresented;
        section.authorizationCodePresented = authCodeEntered;

        if (authCode != section.authorizationCodePresented) {
            cartAdapter.notifyDataSetChanged();
        }
    }

    void onAuthCodeConfirmCancelClicked(int position, String termId, String sectionId) {
        cartAdapter.uncheckCheckbox(position, termId, sectionId);
    }

    public void onAddToCartClicked(View view) {
        if (planPresent) {
            AddToCartConfirmDialogFragment addToCartConfirmDialogFragment = new AddToCartConfirmDialogFragment();
            addToCartConfirmDialogFragment.show(getSupportFragmentManager(), "AddToCartConfirmDialogFragment");
        }
    }

    void onAddToCartConfirmOkClicked() {
        sendEvent(GoogleAnalyticsConstants.CATEGORY_UI_ACTION, GoogleAnalyticsConstants.ACTION_BUTTON_PRESS, "Add to cart", null, moduleName);
        CheckableSectionedListAdapter adapter = (CheckableSectionedListAdapter) searchResultsFragment.getListAdapter();

        String successMessage = "";
        List<Section> updateServerList = new ArrayList<>();
        for (int checkedPosition : adapter.getCheckedPositions()) {
            Cursor cursor = (Cursor) adapter.getItem(checkedPosition);

            String sectionId = cursor.getString(cursor.getColumnIndex(SECTION_ID));
            String termId = cursor.getString(cursor.getColumnIndex(TERM_ID));

            Section section = findSectionInResults(termId, sectionId);
            if (addSectionToCart(section)) {
                successMessage += getString(R.string.registration_added_to_cart_success_format, section.courseName, section.courseSectionNumber) + "\n\n";
                // create list of sections for updating server cart info
                updateServerList.add(section);
            } else {
                successMessage += getString(R.string.registration_added_to_cart_failed_format, section.courseName, section.courseSectionNumber) + "\n\n";
            }
        }
        if (currentCart != null && adapter.getCheckedPositions().size() > 0) {
            cartAdapter = new RegistrationCartListAdapter(this, R.layout.registration_auth_required_header, AUTH_CODE_REQUIRED);
            registeredAdapter = new CheckableSectionedListAdapter(this);
            cartFragment.setListAdapter(cartAdapter);
            registeredFragment.setListAdapter(registeredAdapter);
            fillCartAdapter(currentCart, null);
            fillRegisteredAdapter(currentCart, null);

            // Clear checked positions and remove button
            adapter.clearCheckedPositions();
            adapter.notifyDataSetChanged();
            searchResultsFragment.showAddToCartButton(false, 0);

            // Show success/fail toast
            if (!TextUtils.isEmpty(successMessage)) {
                Toast fillInMessage = Toast.makeText(this, successMessage, Toast.LENGTH_LONG);
                fillInMessage.setGravity(Gravity.CENTER, 0, 0);
                fillInMessage.show();
            }

            if (!updateServerList.isEmpty()) {
                updateServerCart(CartSection.ADD, updateServerList);
            }
        }

    }

    private boolean addSectionToCart(Section section) {
        if (currentCart != null) {
            for (Plan plan : currentCart.plans) {
                for (Term term : plan.terms) {
                    if (term.termId.equals(section.termId)) {
                        for (Section cartSection : term.plannedCourses) {
                            if (cartSection.sectionId.equals(section.sectionId)) {
                                Log.e(TAG, "Can not add section to cart, section already exists in cart.");
                                return false;
                            }
                        }
                        section.classification = Section.CLASSIFICATION_PLANNED;
                        if (section.credits == 0) {
                            section.credits = section.minimumCredits;
                        }
                        Log.d(TAG, "adding section: " + section.courseName + "-" + section.courseSectionNumber);
                        term.plannedCourses = Arrays.copyOf(term.plannedCourses, term.plannedCourses.length + 1);
                        term.plannedCourses[term.plannedCourses.length - 1] = section;
                        return true;
                    }

                }
                for (OpenTerm openTerm : openTerms) {
                    if (openTerm.id.equals(section.termId)) {
                        Term newPlanTerm = new Term();
                        newPlanTerm.termId = openTerm.id;
                        newPlanTerm.name = openTerm.name;
                        newPlanTerm.startDate = openTerm.startDate;
                        newPlanTerm.endDate = openTerm.endDate;
                        newPlanTerm.plannedCourses = new Section[1];
                        section.classification = Section.CLASSIFICATION_PLANNED;
                        if (section.credits == 0) {
                            section.credits = section.minimumCredits;
                        }
                        newPlanTerm.plannedCourses[0] = section;
                        plan.terms = Arrays.copyOf(plan.terms, plan.terms.length + 1);
                        plan.terms[plan.terms.length - 1] = newPlanTerm;
                        return true;
                    }
                }
            }


        } else {
            Log.e(TAG, "currentCart is null");
        }
        return false;
    }

    void removeItemFromCart() {

        int position = cartFragment.getCurrentPosition();
        Cursor cursor = (Cursor) cartFragment.getListView().getItemAtPosition(position);

        String sectionId = cursor.getString(cursor.getColumnIndex(SECTION_ID));
        String termId = cursor.getString(cursor.getColumnIndex(TERM_ID));

        Section section = findAndRemoveSectionFromCart(termId, sectionId);
        // clear the current details and also clear the bundle so it wont be displayed on rotate
        clearDetailFragment();
        cartFragment.setDetailBundle(null);

        List<Section> updateServerList = new ArrayList<>();
        updateServerList.add(section);
        updateServerCart(CartSection.REMOVE, updateServerList);

    }

    private Section findAndRemoveSectionFromCart(String termId, String sectionId) {
        Section returnedSection;
        if (currentCart != null) {
            for (Plan plan : currentCart.plans) {
                for (Term term : plan.terms) {
                    if (term.termId.equals(termId)) {
                        for (Section section : term.plannedCourses) {
                            if (section.sectionId.equals(sectionId)) {
                                returnedSection = section;
                                // remove and re-apply
                                List<Section> tempList = Arrays.asList(term.plannedCourses);
                                ArrayList<Section> sectionList = new ArrayList<>(tempList);

                                sectionList.remove(section);
                                term.plannedCourses = sectionList.toArray(new Section[sectionList.size()]);

                                // update adapters to show the new state
                                cartAdapter = new RegistrationCartListAdapter(this, R.layout.registration_auth_required_header, AUTH_CODE_REQUIRED);
                                registeredAdapter = new CheckableSectionedListAdapter(this);

                                fillCartAdapter(currentCart, null);
                                fillRegisteredAdapter(currentCart, null);
                                cartFragment.setListAdapter(cartAdapter);
                                registeredFragment.setListAdapter(registeredAdapter);

                                return returnedSection;
                            }
                        }
                    }
                }
            }

            Log.e(TAG, "cannot find course in currentCart");
        } else {
            Log.e(TAG, "currentCart is null, cannot find course");
        }
        return null;
    }

    private void updateServerCart(String updateType, List<Section> updateServerList) {

        HashMap<String, List<Section>> termMap = new HashMap<>();

        List<Section> termSections;
        for (Section section : updateServerList) {
            if (termMap.containsKey(section.termId)) {
                termMap.get(section.termId).add(section);
            } else {
                termSections = new ArrayList<>();
                termSections.add(section);
                termMap.put(section.termId, termSections);
            }
        }

        CartPlan plan = new CartPlan();
        plan.planId = currentCart.plans[0].planId;
        plan.terms = new ArrayList<>();

        CartTerm tempTerm;
        for (String termId : termMap.keySet()) {
            tempTerm = new CartTerm();
            tempTerm.termId = termId;
            List<Section> convertList = termMap.get(termId);

            int size = convertList.size();
            tempTerm.sections = new ArrayList<>();

            CartSection currentCartSection;
            for (int i = 0; i < size; i++) {
                Section section = convertList.get(i);
                currentCartSection = new CartSection();
                currentCartSection.sectionId = section.sectionId;
                currentCartSection.action = updateType;
                if (section.ceus > 0) {
                    currentCartSection.ceus = section.ceus;
                } else if (section.selectedCredits != -1) {
                    currentCartSection.credits = section.selectedCredits;
                } else {
                    currentCartSection.credits = section.credits;
                }

                if (TextUtils.isEmpty(section.gradingType)) {
                    Log.d(TAG, "gradingType empty using default");
                    currentCartSection.gradingType = Section.GRADING_TYPE_GRADED;
                } else {
                    currentCartSection.gradingType = section.gradingType;
                }

                tempTerm.sections.add(currentCartSection);
            }
            plan.terms.add(tempTerm);
        }

        String updateCartJson = gson.toJson(plan);
        Log.d(TAG, updateType + " : " + updateCartJson);

        Intent updateServerCartIntent = new Intent(this, RegistrationCartUpdateService.class);
        updateServerCartIntent.putExtra(Extra.REQUEST_URL, requestUrl);
        updateServerCartIntent.putExtra(ModuleMenuAdapter.PLANNING_TOOL,
                getIntent().getBooleanExtra(ModuleMenuAdapter.PLANNING_TOOL, false));
        updateServerCartIntent.putExtra(RegistrationCartUpdateService.SECTIONS_TO_UPDATE, updateCartJson);
        startService(updateServerCartIntent);

    }

    void openRefineSearch() {

        RefineSearchDialogFragment refineSearchDialogFragment = new RefineSearchDialogFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("locationNames", searchFragment.getLocationNames());
        args.putStringArrayList("levelNames", searchFragment.getLevelNames());
        args.putStringArrayList("selectedLocations", searchFragment.selectedLocations);
        args.putStringArrayList("selectedLevels", searchFragment.selectedLevels);
        refineSearchDialogFragment.setArguments(args);
        refineSearchDialogFragment.setCancelable(false);
        refineSearchDialogFragment.show(getSupportFragmentManager(), "refineSearchDialogFragment");

    }

    @Override
    public void onDoneFiltering(ArrayList<String> selectedLocations, ArrayList<String> selectedLevels) {
        if (searchFragment != null) {
            searchFragment.setSearchFilters(selectedLocations, selectedLevels);
        }
    }

    void startSectionSearch(String termId, String pattern, List<String> locationCodes,
                            List<String> levelCodes) {

        String locations = "";
        if (locationCodes != null && locationCodes.size() > 0) {
            locations = TextUtils.join(",", locationCodes);

        }
        String levels = "";
        if (levelCodes != null && levelCodes.size() > 0) {
            levels = TextUtils.join(",", levelCodes);
        }

        searchTask = new SearchSectionTask();
        searchTask.execute(requestUrl, termId, pattern, locations, levels);
        Utils.showProgressIndicator(activity);
    }

    private void fillSearchResultsAdapter(SearchResponse response, Bundle savedInstanceState,
                      boolean addAuthCodeWarning) {

        if (response.sections.length > 0) {
            ArrayList<Section> sections = new ArrayList<>(Arrays.asList(response.sections));

            MatrixCursor sectionedCursor = new MatrixCursor(new String[]{
                    BaseColumns._ID, RegistrationActivity.SECTION_ID, RegistrationActivity.TERM_ID
            });

            int row = 1;

            // First, add any sections that require an AuthCode
            ArrayList<Section> sectionsRequiringAuthCode = sectionsRequiringAuthCode(sections);
            for (Section section : sectionsRequiringAuthCode) {
                Log.d(TAG, "Creating row for : " + section.sectionId + "/" + section.termId);
                sectionedCursor.addRow(new Object[]{"" + row++,
                        section.sectionId, section.termId});
            }

            // Then add all remaining sections
            sections.removeAll(sectionsRequiringAuthCode);

            for (Section section : sections) {
                Log.d(TAG, "Creating row for : " + section.sectionId + "/" + section.termId);
                sectionedCursor.addRow(new Object[]{"" + row++,
                        section.sectionId, section.termId});
            }

            CheckableCursorAdapter checkableAdapter = new CheckableCursorAdapter(
                    this,
                    R.layout.registration_list_checkbox_row,
                    sectionedCursor,
                    new String[]{RegistrationActivity.SECTION_ID},
                    new int[]{R.id.registration_row_layout},
                    0,
                    R.id.checkbox);

            checkableAdapter.setViewBinder(new RegistrationSearchViewBinder());
            checkableAdapter.registerOnCheckBoxClickedListener(new SearchCheckBoxClickedListener());

            if (savedInstanceState != null) {
                boolean[] checkedStatesBooleanArray = savedInstanceState.getBooleanArray("Results");
                if (checkedStatesBooleanArray != null) {
                    checkableAdapter.setCheckedStates(checkedStatesBooleanArray);
                }
            }

            if (addAuthCodeWarning) {
                resultsAdapter.addSection(getString(R.string.registration_approval_required), checkableAdapter);
            } else {
                resultsAdapter.addSection(getString(R.string.search_results_label), checkableAdapter);
            }
        }
    }

    private ArrayList<Section> sectionsRequiringAuthCode(ArrayList<Section> sections) {
        ArrayList<Section> sectionsNeedingAuthCode = new ArrayList<>();
        for (Section section : sections) {
            if (section.authorizationCodeRequired) {
                sectionsNeedingAuthCode.add(section);
            }
        }
        return sectionsNeedingAuthCode;
    }

    Section findSectionInResults(String termId, String sectionId) {
        if (currentResults != null) {

            for (Section section : currentResults.sections) {
                if (section.sectionId.equals(sectionId) && section.termId.equals(termId)) {
                    return section;
                }
            }

            Log.e(TAG, "cannot find section in searchResponse");
            return null;
        } else {
            Log.e(TAG, "searchResponse is null, cannot find section");
            return null;
        }
    }

    private class PlanToRegister {
        String planId;
        SectionRegistration[] sectionRegistrations;
    }

    private class SectionRegistration {
        public String termId;
        String altPin;
        public String sectionId;
        public String action;
        public Float credits;
        public String authorizationCode;
    }

    private class CartPlan {
        String planId;
        public List<CartTerm> terms;
    }

    private class CartTerm {
        public String termId;
        public List<CartSection> sections;
    }

    private class CartSection {
        static final String ADD = "add";
        static final String REMOVE = "remove";

        public String termId;
        public String sectionId;
        public String action;
        public Float credits;
        public Float ceus;
        String gradingType;
    }

    class TermInfoHolder {
        public String termId;
        String termName;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TermInfoHolder)) {
                return false;
            }
            TermInfoHolder holder = (TermInfoHolder) o;
            return holder.termId.equals(this.termId);
        }
    }

    private class CheckEligibilityTask extends AsyncTask<String, Void, EligibilityResponse> {

        @Override
        protected EligibilityResponse doInBackground(String... params) {
            String requestUrl = params[0];

            MobileClient client = new MobileClient(RegistrationActivity.this);
            requestUrl = client.addUserToUrl(requestUrl);
            requestUrl += "/eligibility";

            return client.getEligibility(requestUrl);
        }

        @Override
        protected void onPostExecute(EligibilityResponse result) {

            if (result != null && result.eligible == true) {
                Log.d(TAG, "Eligibility check: true");
            } else {
                Log.d(TAG, "Eligibility check: false");
                String message = "";

                if (result != null && result.messages != null && result.messages.length > 0) {
                    for (Message currentMessage : result.messages) {
                        if (!TextUtils.isEmpty(currentMessage.message)) {
                            if (!TextUtils.isEmpty(message)) {
                                message += "\n\n";
                            }
                            message += currentMessage.message;
                        }
                    }
                }

                cartFragment.setShowEligibilityError(true, message);

                EligibilityDialogFragment eligibilityDialogFragment = EligibilityDialogFragment.newInstance(message);
                if (isInForeground) {
                    eligibilityDialogFragment.show(getSupportFragmentManager(), "EligibilityDialogFragment");
                }

            }

            if (termPinMap == null && result != null && result.terms != null && result.terms.length > 0) {
                setupTermPinMap(result.terms);
            }

            eligibilityChecked = true;
        }

    }

    private class RetrieveCartListTask extends AsyncTask<String, Void, CartResponse> {

        @Override
        protected CartResponse doInBackground(String... params) {
            String requestUrl = params[0];
            boolean planningTool = getIntent().getBooleanExtra(ModuleMenuAdapter.PLANNING_TOOL, false);

            MobileClient client = new MobileClient(RegistrationActivity.this);
            requestUrl = client.addUserToUrl(requestUrl);
            requestUrl += "/plans?planningTool=" + planningTool;

            CartResponse response = client.getCartList(requestUrl);
            return response;
        }

        @Override
        protected void onPostExecute(CartResponse result) {
            currentCart = result;

            enableTabs();

            if (result != null) {
                if (result.plans != null || result.plans.length > 0) {
                    planPresent = true;
                    fillCartAdapter(result, null);
                    fillRegisteredAdapter(result, null);

                    if (cartAdapter.getCount() > 0) {
                        if (isInForeground) {
                            tabLayout.getTabAt(CART_TAB_INDEX).select();
                            Utils.hideProgressIndicator(activity);
                        }
                        return;
                    } else {
                        Log.e(TAG, "Adapter is empty");
                    }
                } else {
                    Log.e(TAG, "No plans returned");
                    planPresent = false;
                }
            } else {
                Log.e(TAG, "Response is null");
                planPresent = false;
            }

            tabLayout.getTabAt(SEARCH_TAB_INDEX).select();
            Utils.hideProgressIndicator(activity);

            if (!planPresent) {
                EligibilityDialogFragment eligibilityDialogFragment =
                        EligibilityDialogFragment.newInstance(getString(R.string.registration_no_plan_eligibility_message));
                if (isInForeground) {
                    eligibilityDialogFragment.show(getSupportFragmentManager(), "PlanNotPresentDialogFragment");
                }
            }
        }
    }

    private class RegisterReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {


            RegistrationActivity.this.sendUserTiming("Registration", System.currentTimeMillis() - startTime, "Registration", "Registration", moduleName);

            String result = intent.getStringExtra(RegisterService.REGISTRATION_RESULT);

            RegistrationResponse registrationResponse;
            Log.d(TAG, "RegisterTask result: " + result);
            if (!TextUtils.isEmpty(result)) {
                registrationResponse = gson.fromJson(result, RegistrationResponse.class);
            } else {
                Log.e(TAG, "result is empty or null");
                return;
            }

            FragmentManager manager = getSupportFragmentManager();
            FragmentTransaction ft = manager.beginTransaction();
            Fragment mainFrame = manager.findFragmentById(R.id.frame_main);

            if (mainFrame != null) {
                ft.detach(mainFrame);
            }

            registerResultsFragment = (RegistrationResultsFragment) Fragment.instantiate(RegistrationActivity.this, RegistrationResultsFragment.class.getName());

            Bundle args = new Bundle();
            args.putParcelable("RegistrationResponse", registrationResponse);
            registerResultsFragment.setArguments(args);

            ft.add(R.id.frame_main, registerResultsFragment, "RegistrationResultsFragment");

            ft.commit();

            if (registrationResponse.successes != null && registrationResponse.successes.length > 0) {
                updateSections(registrationResponse.successes, Section.CLASSIFICATION_REGISTERED);
            }
            if (registrationResponse.failures != null && registrationResponse.failures.length > 0) {
                Log.e(TAG, "failures in registration response found, clearing pins if any");
                clearTermPins();
                for (RegisterSection failure : registrationResponse.failures) {

                    Section section = findSectionInCart(failure.termId, failure.sectionId);
                    String authCode = section.authorizationCodePresented;
                    section.authorizationCodePresented = null;

                    if (authCode != section.authorizationCodePresented) {
                        Log.d(TAG, "Clearing Authorization Code for section that failed registration: " + failure.sectionId);
                        cartAdapter.notifyDataSetChanged();
                    }

                }
            }

            cartAdapter.clearCheckedPositions();
            clearDetailFragment();
            Utils.hideProgressIndicator(activity);

        }

    }

    private class DropReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String result = intent.getStringExtra(RegisterService.REGISTRATION_RESULT);

            RegistrationResponse registrationResponse;
            Log.d(TAG, "Drop result: " + result);
            if (!TextUtils.isEmpty(result)) {
                registrationResponse = gson.fromJson(result, RegistrationResponse.class);
            } else {
                Log.e(TAG, "result is empty or null");
                return;
            }

            FragmentManager manager = getSupportFragmentManager();
            FragmentTransaction ft = manager.beginTransaction();
            Fragment mainFrame = manager.findFragmentById(R.id.frame_main);

            if (mainFrame != null) {
                ft.detach(mainFrame);
            }

            registerResultsFragment = (RegistrationResultsFragment) Fragment.instantiate(RegistrationActivity.this, RegistrationResultsFragment.class.getName());

            Bundle args = new Bundle();
            args.putParcelable("RegistrationResponse", registrationResponse);
            args.putBoolean(RegistrationResultsFragment.METHOD_DROP, true);
            registerResultsFragment.setArguments(args);

            ft.add(R.id.frame_main, registerResultsFragment, "RegistrationResultsFragment");

            ft.commit();

            if (registrationResponse.successes != null && registrationResponse.successes.length > 0) {

                if (getIntent().getBooleanExtra(ModuleMenuAdapter.PLANNING_TOOL, false)) {
                    updateSections(registrationResponse.successes, Section.CLASSIFICATION_PLANNED);
                } else {
                    for (RegisterSection section : registrationResponse.successes) {
                        findAndRemoveSectionFromCart(section.termId, section.sectionId);
                    }
                }

            }
            if (registrationResponse.failures != null && registrationResponse.failures.length > 0) {
                Log.e(TAG, "failures in drop response found, clearing pins if any");
                clearTermPins();
            }

            registeredAdapter.clearCheckedPositions();
            clearDetailFragment();
            Utils.hideProgressIndicator(activity);

        }

    }

    private class CartUpdateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String result = intent.getStringExtra(RegistrationCartUpdateService.UPDATE_RESULT);

            UpdateResponse updateResponse;
            Log.d(TAG, "Cart update result: " + result);
            if (!TextUtils.isEmpty(result)) {
                updateResponse = gson.fromJson(result, UpdateResponse.class);
            } else {
                Log.e(TAG, "result is empty or null");
                return;
            }

            if (updateResponse.success) {
                Log.d(TAG, "Add to server cart successful");
            } else {
                Log.e(TAG, "Add to server cart failed");
                Toast updateToast = Toast.makeText(context,
                        getString(R.string.registration_added_to_server_cart_failed), Toast.LENGTH_LONG);
                updateToast.setGravity(Gravity.CENTER, 0, 0);
                updateToast.show();
            }
        }
    }

    private class SearchSectionTask extends AsyncTask<String, Void, SearchResponse> {

        @Override
        protected SearchResponse doInBackground(String... params) {
            String requestUrl = params[0];
            String termId = params[1];
            String pattern = params[2];
            String locations = params[3];
            String levels = params[4];
            String encodedTermId = "";
            String encodedPattern = "";
            try {
                encodedTermId = URLEncoder.encode(termId, "UTF-8");
                encodedPattern = URLEncoder.encode(pattern, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "UnsupportedEncodingException:", e);
            }

            MobileClient client = new MobileClient(RegistrationActivity.this);
            requestUrl = client.addUserToUrl(requestUrl);
            requestUrl += "/search-courses?pattern=" + encodedPattern + "&term=" + encodedTermId;

            if (!TextUtils.isEmpty(locations)) {
                try {
                    requestUrl += "&locations=" + URLEncoder.encode(locations, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "UnsupportedEncodingException:", e);
                }
            }
            if (!TextUtils.isEmpty(levels)) {
                try {
                    requestUrl += "&academicLevels=" + URLEncoder.encode(levels, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "UnsupportedEncodingException:", e);
                }
            }

            SearchResponse jsonResponse = client.findSections(requestUrl);

            return jsonResponse;
        }

        @Override
        protected void onPostExecute(SearchResponse result) {

            if (this != null) {
                currentResults = result;

                boolean addAuthCodeWarning = false;
                for (Section section : result.sections) {
                    if (section.authorizationCodeRequired) {
                        addAuthCodeWarning = true;
                    }
                }

                if (addAuthCodeWarning) {
                    resultsAdapter = new CheckableSectionedListAdapter(RegistrationActivity.this, R.layout.registration_search_results_header);
                } else {
                    resultsAdapter = new CheckableSectionedListAdapter(RegistrationActivity.this);
                }

                FragmentManager manager = getSupportFragmentManager();
                FragmentTransaction ft = manager.beginTransaction();
                searchResultsFragment = (RegistrationSearchResultsListFragment)
                        manager.findFragmentByTag("RegistrationSearchResultsListFragment");

                clearMainFragment();

                if (searchResultsFragment == null) {
                    searchResultsFragment = (RegistrationSearchResultsListFragment) RegistrationSearchResultsListFragment.newInstance(
                            RegistrationActivity.this, RegistrationSearchResultsListFragment.class.getName(), null);
                    searchResultsFragment.setNewSearch(true);
                    searchResultsFragment.setListAdapter(resultsAdapter);
                    ft.add(R.id.frame_main, searchResultsFragment, "RegistrationSearchResultsListFragment");
                } else {
                    searchResultsFragment.setNewSearch(true);
                    searchResultsFragment.setListAdapter(resultsAdapter);
                    ft.attach(searchResultsFragment);
                }
                ft.commitAllowingStateLoss();

                if (currentResults != null) {

                    if (currentResults.sections != null && currentResults.sections.length > 0) {
                        fillSearchResultsAdapter(currentResults, null, addAuthCodeWarning);
                    } else {
                        Log.e(TAG, "Sections array null or empty");
                        resultsAdapter = null;
                    }
                } else {
                    Log.e(TAG, "Search response is null");
                    resultsAdapter = null;
                }
                Utils.hideProgressIndicator(activity);

            }
        }
    }

    private class RegistrationTabListener implements TabLayout.OnTabSelectedListener {
        private final FragmentManager fragmentManager;
        private int fragmentContainerResId;
        private final Context mContext;
        private String mTag;
        private Class<? extends Fragment> mClass;
        private Fragment mFragment;

        RegistrationTabListener(Context context, int fragmentContainerResId) {
            fragmentManager = getSupportFragmentManager();
            mContext = context;
            this.fragmentContainerResId = fragmentContainerResId;
        }

        /* The following are each of the TabLayout.OnTabSelectedListener callbacks */
        @Override
        public void onTabSelected(Tab tab) {
            if (tab.getTag() != null) {
                if (!((Boolean) tab.getTag())) {
                    return;
                }
            }

            determineTab(tab.getPosition());
            clearMainFragment();
            clearDetailFragment();
            FragmentTransaction ft = fragmentManager.beginTransaction();

            // Check if the fragment is in the Fragment Manager
            if (fragmentManager.findFragmentByTag(mTag) == null) {
                if (mFragment == null) {
                    mFragment = Fragment.instantiate(mContext, mClass.getName());
                }

                if (fragmentContainerResId == 0) {
                    fragmentContainerResId = android.R.id.content;
                }

                ft.add(fragmentContainerResId, mFragment, mTag);

            } else {
                // If it exists, simply attach it in order to show it
                ft.attach(mFragment);
            }
            currentTab = tab.getPosition();
            ft.commitAllowingStateLoss();
        }

        private void determineTab(int tabPosition) {
            switch (tabPosition) {
                case 2: // Register
                    mClass = RegistrationRegisteredListFragment.class;
                    mTag = "RegistrationRegisteredListFragment";
                    mFragment = registeredFragment;
                    break;
                case 1: // Search
                    mClass = RegistrationSearchFragment.class;
                    mTag = "RegistrationSearchFragment";
                    mFragment = searchFragment;
                    break;
                case 0: // Cart
                    mClass = RegistrationCartListFragment.class;
                    mTag = "RegistrationCartListFragment";
                    mFragment = cartFragment;
                    break;
            }
        }

        @Override
        public void onTabUnselected(Tab tab) {
            clearDetailFragment();
            determineTab(tab.getPosition());
            FragmentTransaction ft = fragmentManager.beginTransaction();

            if (cartFragment != null) {
                // Detach the fragment, because another one is being attached
                ft.detach(cartFragment);
            }

            Fragment fragment = fragmentManager.findFragmentByTag(tab.getText().toString());
            if (fragment != null) {
                ft.detach(cartFragment);
            }
            ft.commitAllowingStateLoss();
        }

        @Override
        public void onTabReselected(Tab tab) {
            onTabSelected(tab);
        }
    }

    private class RegistrationViewBinder implements SimpleCursorAdapter.ViewBinder {
        @Override
        public boolean setViewValue(View view, Cursor cursor, int index) {
            if (index == cursor.getColumnIndex(SECTION_ID)) {
                String sectionId = cursor.getString(index);
                String termId = cursor.getString(cursor.getColumnIndex(TERM_ID));

                Section section = findSectionInCart(termId, sectionId);

                setRowView(section, view);

                return true;
            }
            return false;
        }
    }

    private class RegistrationSearchViewBinder implements SimpleCursorAdapter.ViewBinder {
        @Override
        public boolean setViewValue(View view, Cursor cursor, int index) {
            if (index == cursor.getColumnIndex(RegistrationActivity.SECTION_ID)) {
                String sectionId = cursor.getString(index);
                String termId = cursor.getString(cursor.getColumnIndex(RegistrationActivity.TERM_ID));

                Section section = findSectionInResults(termId, sectionId);

                setRowView(section, view);
                return true;
            }
            return false;
        }
    }

    private void setRowView(Section section, View view) {
        // Reset views to visible for next view
        view.findViewById(R.id.instructor_credits_separator).setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(section.courseName)) {
            String titleString = section.courseName;
            if (!TextUtils.isEmpty(section.courseSectionNumber)) {
                titleString = getString(R.string.default_course_section_format,
                        section.courseName,
                        section.courseSectionNumber);
            }
            TextView courseNameView = (TextView) view.findViewById(R.id.course_name);
            courseNameView.setText(titleString);
        }

        // For Search Tab Only
        if (searchResultsFragment != null && searchResultsFragment.isAdded()) {
            String academicLevels = "";
            if (section.academicLevels != null && section.academicLevels.length > 0) {
                academicLevels = TextUtils.join(",", section.academicLevels);
            }

            String levelAndLocationText = "";
            TextView levelAndLocationView = (TextView) view.findViewById(R.id.academic_level_and_location);
            if (!TextUtils.isEmpty(academicLevels) && !TextUtils.isEmpty(section.location)) {
                levelAndLocationText = getString(R.string.default_two_string_separator_format,
                        academicLevels,
                        section.location);

            } else if (!TextUtils.isEmpty(academicLevels)) {
                levelAndLocationText = academicLevels;
            } else if (!TextUtils.isEmpty(section.location)) {
                levelAndLocationText = section.location;
            }

            levelAndLocationView.setText(levelAndLocationText);

            // Available/Capacity images & text
            if (section.available != null && section.capacity != null) {
                String capacityText = getString(R.string.remaining_capacity,
                        section.available,
                        section.capacity);
                Drawable meterImage = null;
                meterImage = RegistrationDetailFragment.getMeterImage(section, this);

                RelativeLayout seatsAvailView = (RelativeLayout) view.findViewById(R.id.seats_available_box);
                seatsAvailView.setVisibility(View.VISIBLE);
                if (meterImage != null) {
                    VersionSupportUtils.enableMirroredDrawable(meterImage);
                    ((ImageView) seatsAvailView.findViewById(R.id.seats_available_image)).setImageDrawable(meterImage);
                }
                ((TextView) seatsAvailView.findViewById(R.id.seats_available_text)).setText(capacityText);
            }

            // Authorization Code
            handleAuthorizationCode(section, view);
        }

        // For Cart Tab Only
        if (cartFragment != null && cartFragment.isAdded()) {
            handleAuthorizationCode(section, view);
        }

        if (!TextUtils.isEmpty(section.sectionTitle)) {
            TextView sectionTitleView = (TextView) view.findViewById(R.id.section_title);
            sectionTitleView.setText(section.sectionTitle);
        }

        TextView instructorView = (TextView) view.findViewById(R.id.instructor);
        if (section.instructors != null && section.instructors.length != 0) {
            String instructorNames = "";
            for (Instructor instructor : section.instructors) {

                if (!TextUtils.isEmpty(instructorNames)) {
                    instructorNames += " ; ";
                }

                if (!TextUtils.isEmpty(instructor.lastName)) {
                    String shortName = "";
                    if (!TextUtils.isEmpty(instructor.firstName)) {
                        shortName += getString(R.string.default_last_name_first_initial_format,
                                instructor.lastName,
                                instructor.firstName.charAt(0));
                    } else {
                        shortName = instructor.lastName;
                    }
                    instructorNames += shortName;
                } else if (!TextUtils.isEmpty(instructor.formattedName)) {
                    instructorNames += instructor.formattedName;
                }

            }

            instructorView.setText(instructorNames);
        } else {
            view.findViewById(R.id.instructor_credits_separator).setVisibility(View.GONE);
        }

        TextView creditsView = (TextView) view.findViewById(R.id.credits);
        String creditsString = "";

        if (section.selectedCredits != -1) {

            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                creditsString = getString(R.string.registration_row_credits_with_type_format,
                        section.selectedCredits,
                        getString(R.string.registration_credits),
                        getString(R.string.registration_audit));
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                creditsString = getString(R.string.registration_row_credits_with_type_format,
                        section.selectedCredits,
                        getString(R.string.registration_credits),
                        getString(R.string.registration_pass_fail_abbrev));
            } else {
                creditsString = getString(R.string.registration_row_credits_format,
                        section.selectedCredits,
                        getString(R.string.registration_credits));
            }

        } else if (section.credits != 0) {

            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                creditsString = getString(R.string.registration_row_credits_with_type_format,
                        section.credits,
                        getString(R.string.registration_credits),
                        getString(R.string.registration_audit));
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                creditsString = getString(R.string.registration_row_credits_with_type_format,
                        section.credits,
                        getString(R.string.registration_credits),
                        getString(R.string.registration_pass_fail_abbrev));
            } else {
                creditsString = getString(R.string.registration_row_credits_format,
                        section.credits,
                        getString(R.string.registration_credits));
            }

        } else if ((!TextUtils.isEmpty(section.variableCreditOperator) && section.variableCreditOperator.equals(Section.VARIABLE_OPERATOR_OR))
                || section.minimumCredits != 0) {

            if (section.maximumCredits != 0) {
                creditsString = getString(R.string.registration_row_credits_min_max_format,
                        section.minimumCredits,
                        section.maximumCredits,
                        getString(R.string.registration_credits));
            } else {
                creditsString = getString(R.string.registration_row_credits_format,
                        section.minimumCredits,
                        getString(R.string.registration_credits));
            }

        } else if (section.ceus != 0) {
            creditsString = getString(R.string.registration_row_credits_format,
                    section.ceus,
                    getString(R.string.registration_ceus));
        } else {
            // Only want to display zero in last possible case to avoid not showing the correct alternative
            if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_AUDIT)) {
                creditsString = getString(R.string.registration_row_credits_with_type_format,
                        0f,
                        getString(R.string.registration_credits),
                        getString(R.string.registration_audit));
            } else if (!TextUtils.isEmpty(section.gradingType) && section.gradingType.equals(Section.GRADING_TYPE_PASS_FAIL)) {
                creditsString = getString(R.string.registration_row_credits_with_type_format,
                        0f,
                        getString(R.string.registration_credits),
                        getString(R.string.registration_pass_fail_abbrev));
            } else {
                creditsString = getString(R.string.registration_row_credits_format,
                        0f,
                        getString(R.string.registration_credits));
            }
        }
        creditsView.setText(creditsString);



        TextView meetingsTypeView = (TextView) view.findViewById(R.id.meetings_and_type);
        if (section.meetingPatterns != null && section.meetingPatterns.length != 0) {

            String meetingsString = "";

            for (MeetingPattern pattern : section.meetingPatterns) {

                if (!TextUtils.isEmpty(meetingsString)) {
                    meetingsString += " ; ";
                }

                String daysString = "";
                if (pattern.daysOfWeek != null && pattern.daysOfWeek.length != 0) {

                    for (int dayNumber : pattern.daysOfWeek) {

                        if (!TextUtils.isEmpty(daysString)) {
                            daysString += ", ";
                        }
                        // Adding 1 to number to make the Calendar constants
                        daysString += CalendarUtils.getDayShortName(dayNumber);
                    }

                }

                Date startTimeDate = null;
                Date endTimeDate = null;
                String displayStartTime = "";
                String displayEndTime = "";

                try {
                    if (!TextUtils.isEmpty(pattern.sisStartTimeWTz) && pattern.sisStartTimeWTz.contains(" ")) {
                        String[] splitTimeAndZone = pattern.sisStartTimeWTz.split(" ");
                        String time = splitTimeAndZone[0];
                        String timeZone = splitTimeAndZone[1];
                        altTimeParserFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
                        startTimeDate = altTimeParserFormat.parse(time);
                    } else if (!TextUtils.isEmpty(pattern.startTime)) {
                        startTimeDate = defaultTimeParserFormat.parse(pattern.startTime);
                    }

                    if (!TextUtils.isEmpty(pattern.sisEndTimeWTz) && pattern.sisEndTimeWTz.contains(" ")) {
                        String[] splitTimeAndZone = pattern.sisEndTimeWTz.split(" ");
                        String time = splitTimeAndZone[0];
                        String timeZone = splitTimeAndZone[1];
                        altTimeParserFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
                        endTimeDate = altTimeParserFormat.parse(time);
                    } else if (!TextUtils.isEmpty(pattern.endTime)) {
                        endTimeDate = defaultTimeParserFormat.parse(pattern.endTime);
                    }

                    if (startTimeDate != null) {
                        displayStartTime = timeFormatter.format(startTimeDate);
                    }
                    if (endTimeDate != null) {
                        displayEndTime = timeFormatter.format(endTimeDate);
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "ParseException: ", e);
                }

                if (!TextUtils.isEmpty(displayStartTime)) {

                    if (!TextUtils.isEmpty(pattern.instructionalMethodCode)) {
                        meetingsString += getString(R.string.default_meeting_days_times_and_type_format,
                                daysString,
                                displayStartTime,
                                displayEndTime,
                                pattern.instructionalMethodCode);
                    } else {
                        meetingsString += getString(R.string.default_meeting_days_and_times_format,
                                daysString,
                                displayStartTime,
                                displayEndTime);
                    }

                } else {
                    meetingsString += daysString;
                }

            }

            if (!TextUtils.isEmpty(meetingsString)) {
                meetingsTypeView.setText(meetingsString);
            }
        } else {
            meetingsTypeView.setVisibility(View.GONE);
        }
    }

    private void handleAuthorizationCode(Section section, View view) {
        if (section.authorizationCodeRequired) {
            LinearLayout ll = (LinearLayout) view.findViewById(R.id.registration_row_layout);
            ll.setBackgroundColor(VersionSupportUtils.getColorHelper(this, R.color.status_important_text_light_color));
        }
    }

    private class RegisterCheckBoxClickedListener implements OnCheckBoxClickedListener {

        @Override
        public void onCheckBoxClicked(CheckBox checkBox, boolean isChecked, int position) {

            if (isChecked || !cartAdapter.getCheckedPositions().isEmpty()) {
                cartFragment.showRegisterButton(true, cartAdapter.getCheckedPositions().size());
            } else {
                cartFragment.showRegisterButton(false, 0);
            }
        }
    }

    // Click listener for sections that require an Authorization Code
    private class RegisterAuthCodeCheckBoxClickedListener implements RegistrationCartCheckableAdapter.OnCartCheckBoxClickedListener {
        @Override
        public void onCartCheckBoxClicked(CheckBox checkBox, boolean isChecked, int position, String termId, String sectionId) {
            Log.e(TAG, "onCheckBoxClicked: position/term/section" + position +"/"+termId+"/"+sectionId );

            if (isChecked || !cartAdapter.getCheckedPositions().isEmpty()) {
                if (isChecked) {
                    // Have to add 1 to position because of the header
                    Section section = findSectionInCart(termId, sectionId);

                    if (section != null
                            && (section.authorizationCodeRequired && TextUtils.isEmpty(section.authorizationCodePresented))) {

                        AuthCodeConfirmDialogFragment authCodeDialogFragment = AuthCodeConfirmDialogFragment.newInstance(section, position);
                        // Stops the back button from closing dialog
                        authCodeDialogFragment.setCancelable(false);
                        authCodeDialogFragment.show(getSupportFragmentManager(), "AuthCodeConfirmDialogFragment");
                    }

                }
                cartFragment.showRegisterButton(true, cartAdapter.getCheckedPositions().size());
            } else {
                cartFragment.showRegisterButton(false, 0);
            }

        }
    }
    
    private class DropCheckBoxClickedListener implements OnCheckBoxClickedListener {

        @Override
        public void onCheckBoxClicked(CheckBox checkBox, boolean isChecked, int position) {

            if (isChecked || !registeredAdapter.getCheckedPositions().isEmpty()) {
                registeredFragment.showDropButton(true, registeredAdapter.getCheckedPositions().size());
            } else {
                registeredFragment.showDropButton(false, 0);
            }
        }
    }

    private class SearchCheckBoxClickedListener implements OnCheckBoxClickedListener {

        @Override
        public void onCheckBoxClicked(CheckBox checkBox, boolean isChecked, int position) {

            if (isChecked || !resultsAdapter.getCheckedPositions().isEmpty()) {

                if (isChecked) {
                    // Have to add 1 to position because of the header
                    Cursor cursor = (Cursor) resultsAdapter.getItem(position + 1);

                    String sectionId = cursor.getString(cursor.getColumnIndex(RegistrationActivity.SECTION_ID));
                    String termId = cursor.getString(cursor.getColumnIndex(RegistrationActivity.TERM_ID));

                    Section section = findSectionInResults(termId, sectionId);

                    if (section != null
                            && (!TextUtils.isEmpty(section.variableCreditOperator) && section.variableCreditOperator.equals(Section.VARIABLE_OPERATOR_OR))
                            || (section.minimumCredits != 0 && section.maximumCredits != 0)) {

                        VariableCreditsConfirmDialogFragment creditsDialogFragment = VariableCreditsConfirmDialogFragment.newInstance(section, position);
                        // Stops the back button from closing dialog
                        creditsDialogFragment.setCancelable(false);
                        creditsDialogFragment.show(getSupportFragmentManager(), "VariableCreditsConfirmDialogFragment");
                    }

                }
                searchResultsFragment.showAddToCartButton(true, resultsAdapter.getCheckedPositions().size());
            } else {
                searchResultsFragment.showAddToCartButton(false, 0);
            }

        }
    }

}
