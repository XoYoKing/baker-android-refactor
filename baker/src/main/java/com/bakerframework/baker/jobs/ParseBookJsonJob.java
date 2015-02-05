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
package com.bakerframework.baker.jobs;

import android.util.Log;

import com.bakerframework.baker.BakerApplication;
import com.bakerframework.baker.R;
import com.bakerframework.baker.events.ParseBookJsonCompleteEvent;
import com.bakerframework.baker.events.ParseBookJsonErrorEvent;
import com.bakerframework.baker.helper.FileHelper;
import com.bakerframework.baker.model.BookJson;
import com.bakerframework.baker.model.Issue;
import com.bakerframework.baker.settings.Configuration;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import de.greenrobot.event.EventBus;

public class ParseBookJsonJob extends Job {
    private final Issue issue;
    private boolean completed;

    public ParseBookJsonJob(Issue issue) {
        super(new Params(Priority.MID).setPersistent(false));
        this.issue = issue;
        this.completed = false;
        Log.i("ParseBookJsonJob", "JOB CREATED");
    }

    @Override
    public void onAdded() {
        Log.i("ParseBookJsonJob", "JOB ADDED");
    }

    @Override
    public void onRun() throws Throwable {
        Log.i("ParseBookJsonJob", "start");

        // Prepare directory;
        File bookJsonDirectory = new File(Configuration.getMagazinesDirectory(), issue.getName());
        File bookJsonFile = new File(bookJsonDirectory, BakerApplication.getInstance().getString(R.string.path_book));

        // Read book json
        JSONObject jsonObject = FileHelper.getJsonObjectFromFile(bookJsonFile);

        // Validate book json
        this.validateJson(jsonObject);

        // Create json result
        BookJson bookJson = new BookJson();
        bookJson.fromJson(jsonObject);
        bookJson.setIssueName(this.issue.getName());

        // Post complete event
        completed = true;
        Log.i("ParseBookJsonJob", "completed");
        EventBus.getDefault().post(new ParseBookJsonCompleteEvent(issue, bookJson));
    }

    @Override
    protected void onCancel() {
        completed = true;
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        Log.e("DownloadIssueJob", throwable.getLocalizedMessage());
        EventBus.getDefault().post(new ParseBookJsonErrorEvent(issue, throwable));
        return false;
    }

    private void validateJson(final JSONObject json) throws Exception {
        for (String property : getRequiredProperties()) {
            if (!json.has(property)) {
                throw new MissingPropertyException(property);
            }
        }
        JSONArray contents = new JSONArray(json.getString("contents"));
        if (contents.length() < 0) {
            throw new MissingContentException();
        }
    }

    private String[] getRequiredProperties() {
        return new String[]{"contents"};
    }

    public Issue getIssue() {
        return issue;
    }

    public boolean isCompleted() {
        return completed;
    }

    private class MissingPropertyException extends Exception {
        private final String property;

        public MissingPropertyException(String property) {
            this.property = property;
        }

        public String getProperty() {
            return property;
        }
    }

    private class MissingContentException extends Exception {

    }

}
