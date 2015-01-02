/**
 * Copyright (c) 2013-2014. Francisco Contreras, Holland Salazar.
 * Copyright (c) 2015. Tobias Strebitzer, Francisco Contreras, Holland Salazar.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 * Neither the name of the Baker Framework nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/
package com.bakerframework.baker.activity;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;

import com.bakerframework.baker.BakerApplication;
import com.bakerframework.baker.R;
import com.bakerframework.baker.adapter.IssueAdapter;
import com.bakerframework.baker.model.*;
import com.bakerframework.baker.model.IssueCollection;
import com.bakerframework.baker.settings.Configuration;
import com.bakerframework.baker.settings.SettingsActivity;
import com.bakerframework.baker.view.IssueCardView;
import com.bakerframework.baker.view.ShelfView;

import org.json.JSONException;
import org.solovyev.android.checkout.ActivityCheckout;
import org.solovyev.android.checkout.BillingRequests;
import org.solovyev.android.checkout.Checkout;
import org.solovyev.android.checkout.Inventory;
import org.solovyev.android.checkout.Purchase;
import org.solovyev.android.checkout.RequestListener;
import org.solovyev.android.checkout.ResponseCodes;

import java.util.ArrayList;
import java.util.List;

public class ShelfActivity extends ActionBarActivity implements IssueCollectionListener, SwipeRefreshLayout.OnRefreshListener {

    public static final int STANDALONE_MAGAZINE_ACTIVITY_FINISH = 1;

    // Issues
    private ShelfView shelfView;
    private IssueAdapter issueAdapter;
    private IssueCollection issueCollection;

    // Features
    private SwipeRefreshLayout swipeRefreshLayout;
    private DrawerLayout drawerLayout;
    private ListView drawerList;

    // Billing
    @NonNull
    private final ActivityCheckout checkout = Checkout.forActivity(this, BakerApplication.getInstance().getCheckout());
    @NonNull
    private Inventory inventory;

    public ActivityCheckout getCheckout() {
        return checkout;
    }

    /**
     * Used when running in standalone mode based on the run_as_standalone setting in booleans.xml.
     */
    private boolean STANDALONE_MODE = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        STANDALONE_MODE = getResources().getBoolean(R.bool.run_as_standalone);

        // Initialize tutorial
        if(Configuration.getPrefFirstTimeRun()) {
            Log.d(this.getClass().getName(), "First time app running, launching tutorial.");
            showAppUsage();
        }

        // Prepare google analytics
        if (getResources().getBoolean(R.bool.ga_enable) && getResources().getBoolean(R.bool.ga_register_app_open_event)) {
            ((BakerApplication) this.getApplication()).sendEvent(getString(R.string.application_category), getString(R.string.application_open), getString(R.string.application_open_label));
        }

        // Initialize issue collection
        issueCollection = BakerApplication.getInstance().getIssueCollection();
        issueCollection.addListener(this);

        // Initialize issue adapter for shelf view
        issueAdapter = new IssueAdapter(this, issueCollection);

        // Render View
        this.setContentView(R.layout.shelf_activity);

        // Initialize Features
        setupHeader();
        loadBackground();
        setupSwipeLayout();
        setupActionBar();
        setupCategoryDrawer();

        // Fade in animation
        View view = findViewById(android.R.id.content);
        Animation mLoadAnimation = AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.fade_in);
        mLoadAnimation.setDuration(2000);
        view.startAnimation(mLoadAnimation);

        // Initialize shelf view
        shelfView = (ShelfView) findViewById(R.id.shelf_view);
        shelfView.setAdapter(issueAdapter);
        issueAdapter.updateIssues();

        // Update category drawer
        updateCategoryDrawer(issueCollection.getCategories(), issueAdapter.getCategoryIndex());

        // Continue downloads
        unzipPendingPackages();

        // Initialize checkout
        checkout.start();
        // you only need this if this activity starts purchase process
        checkout.createPurchaseFlow(new PurchaseListener());
        // you only need this if this activity needs information about purchases/SKUs
        inventory = checkout.loadInventory();
        inventory.whenLoaded(new InventoryLoadedListener());

    }

    private class PurchaseListener extends BaseRequestListener<Purchase> {
        @Override
        public void onSuccess(@NonNull Purchase purchase) {
            onPurchased();
        }

        private void onPurchased() {
            // let's update purchase information in local inventory
            inventory.load().whenLoaded(new InventoryLoadedListener());
            Toast.makeText(getApplicationContext(), getString(R.string.purchase_thank_you_message), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(int response, @NonNull Exception e) {
            // it is possible that our data is not synchronized with data on Google Play => need to handle some errors
            if (response == ResponseCodes.ITEM_ALREADY_OWNED) {
                onPurchased();
            } else {
                super.onError(response, e);
            }
        }
    }

    private abstract class BaseRequestListener<R> implements RequestListener<R> {

        @Override
        public void onError(int response, @NonNull Exception e) {
            // @TODO: add alert dialog or logging
        }
    }

    private class InventoryLoadedListener implements Inventory.Listener {
        @Override
        public void onLoaded(@NonNull Inventory.Products inventoryProducts) {
            BakerApplication.getInstance().getIssueCollection().updatePricesFromProducts(inventoryProducts);
            issueAdapter.updateIssues();
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void setupSwipeLayout() {
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        swipeRefreshLayout.setOnRefreshListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!BakerApplication.getInstance().checkPlayServices(this)) {
            // finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.shelf, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Show / Hide subscription menu
        if(!getString(R.string.subscription_product_id).isEmpty()) {
            menu.getItem(0).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.action_info) {
            Intent intent = new Intent(this, InfoActivity.class);
            intent.putExtra(IssueActivity.MODAL_URL, getString(R.string.infoUrl));
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        } else if (itemId == R.id.action_refresh) {
            swipeRefreshLayout.setRefreshing(true);
            if(!issueCollection.isLoading()) {
                issueCollection.reload();
            }
            return true;
        } else if (itemId == R.id.action_subscribe) {

            if(issueCollection.getSubscriptionSku() != null) {
                final ActivityCheckout checkout = this.getCheckout();
                checkout.whenReady(new Checkout.ListenerAdapter() {
                    @Override
                    public void onReady(@NonNull BillingRequests requests) {
                        requests.purchase(issueCollection.getSubscriptionSku(), null, checkout.getPurchaseFlow());
                    }
                });
                return true;
            }else{
                Toast.makeText(this, "Subscriptions are currently not available!", Toast.LENGTH_LONG).show();
                return false;
            }
        } else {
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onIssueCollectionLoaded() {
        presentIssues();
    }

    public void presentIssues() {
        // Update Shelf Spinner
        swipeRefreshLayout.setRefreshing(false);

        // Update category drawer
        updateCategoryDrawer(issueCollection.getCategories(), issueAdapter.getCategoryIndex());

        // Update shelf view adapter
        issueAdapter.updateIssues();

        // Continue downloads
        unzipPendingPackages();
    }

    @Override
    public void onIssueCollectionLoadError() {

    }

    private void loadBackground() {
        WebView webview = (WebView) findViewById(R.id.backgroundWebView);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setBackgroundColor(Color.TRANSPARENT);
        webview.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
        webview.loadUrl(getString(R.string.backgroundUrl));
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setupCategoryDrawer() {

        // Create layout
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        // Create list
        drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerList.setOnItemClickListener(new DrawerItemClickListener());

        // Set up drawer toggle
        Button drawerToggle = (Button) findViewById(R.id.category_toggle);
        drawerToggle.setOnClickListener(new CategoryToggleClickListener());
    }

    private void updateCategoryDrawer(List<String> categories, int position) {
        Log.d(this.getClass().getName(), "CATEGORIES: " + categories.size() + "; POSITION: " + position);
        if(categories.size() == 0) {
            ((Button) findViewById(R.id.category_toggle)).setVisibility(View.GONE);
        }else{
            drawerList.setAdapter(new ArrayAdapter<>(this, R.layout.drawer_list_item, categories));
            drawerList.setItemChecked(position, true);
        }
    }

    private void setupHeader() {
        WebView webview = (WebView) findViewById(R.id.headerView);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setUseWideViewPort(true);
        webview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        webview.setBackgroundColor(Color.TRANSPARENT);
        webview.setWebChromeClient(new WebChromeClient());
        webview.loadUrl(getString(R.string.headerUrl));
    }

    private void setupActionBar() {
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(R.layout.shelf_actionbar);
    }

    public void viewMagazine(final BookJson book) {
        Intent intent = new Intent(this, IssueActivity.class);
        try {
            intent.putExtra(Configuration.BOOK_JSON_KEY, book.toJSON().toString());
            intent.putExtra(Configuration.ISSUE_NAME, book.getMagazineName());
            intent.putExtra(Configuration.ISSUE_STANDALONE, STANDALONE_MODE);
            startActivityForResult(intent, STANDALONE_MAGAZINE_ACTIVITY_FINISH);
        } catch (JSONException e) {
            Toast.makeText(this, "The book.json is invalid.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public void showAppUsage() {
        BookJson book = new BookJson();
        book.setMagazineName(this.getString(R.string.ut_directory));
        List<String> contents = new ArrayList<>();
        String pages[] = this.getString(R.string.ut_pages).split(">");
        for (String page : pages) {
            page = page.trim();
            contents.add(page);
        }
        book.setContents(contents);
        book.setOrientation("portrait");
        Intent intent = new Intent(this, IssueActivity.class);

        try {
            intent.putExtra(Configuration.BOOK_JSON_KEY, book.toJSON().toString());
            intent.putExtra(Configuration.ISSUE_NAME, book.getMagazineName());
            intent.putExtra(Configuration.ISSUE_RETURN_TO_SHELF, true);
            intent.putExtra(Configuration.ISSUE_ENABLE_DOUBLE_TAP, false);
            intent.putExtra(Configuration.ISSUE_ENABLE_BACK_NEXT_BUTTONS, true);
            intent.putExtra(Configuration.ISSUE_ENABLE_TUTORIAL, true);
            startActivityForResult(intent, STANDALONE_MAGAZINE_ACTIVITY_FINISH);
        } catch (JSONException e) {
            Log.e(this.getClass().getName(), "Error parsing book json", e);
        }

    }

    private void unzipPendingPackages() {
        if(shelfView != null) {
            // @TODO: Make it rain!
            for (int i = 0; i < shelfView.getChildCount(); i++) {
                IssueCardView issueCardView = (IssueCardView) shelfView.getChildAt(i);
                if(issueCardView.getIssue().isDownloaded() && !issueCardView.getIssue().isDownloading()) {
                    // Continue issue extraction
                    Log.d(this.getClass().toString(), "Continue unzip of " + issueCardView.getIssue().getName());
                    issueCardView.startUnzip();
                }
            }
        }

    }

    @Override
    public void onBackPressed() {
        /* @TODO: Check if any downloading or unpacking process is currently running */
        final List<Issue> downloadingIssues = issueCollection.getDownloadingIssues();
        if (downloadingIssues.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                .setTitle(this.getString(R.string.exit))
                .setMessage(this.getString(R.string.closing_app))
                .setPositiveButton(this.getString(R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        issueCollection.cancelDownloadingIssues(downloadingIssues);
                        ShelfActivity.super.onBackPressed();
                    }
                })
                .setNegativeButton(this.getString(R.string.no), null)
                .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(this.getClass().getName(), "MagazineActivity finished, resultCode: " + resultCode);
        if (resultCode == STANDALONE_MAGAZINE_ACTIVITY_FINISH) {
            this.finish();
        }
    }

    @Override
    public void onRefresh() {
        issueCollection.reload();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // Categories

    /* The click listener for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
            onCategorySelected(index);
        }
    }

    /* The click listener for the category toggle */
    private class CategoryToggleClickListener implements ImageButton.OnClickListener {
        @Override
        public void onClick(View view) {
            if(drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START);
            }else{
                drawerLayout.openDrawer(Gravity.START);
            }
        }
    }

    private void onCategorySelected(int index) {
        String category = issueCollection.getCategories().get(index);
        issueAdapter.setCategory(category);

        // Update category drawer UI
        drawerList.setItemChecked(index, true);
        ((Button) findViewById(R.id.category_toggle)).setText(category);

        // Close category drawer
        drawerLayout.closeDrawer(drawerList);
    }

}