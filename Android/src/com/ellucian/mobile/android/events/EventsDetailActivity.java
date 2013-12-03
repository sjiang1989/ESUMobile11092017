package com.ellucian.mobile.android.events;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.ellucian.mobile.android.R;
import com.ellucian.mobile.android.DataCache;
import com.ellucian.mobile.android.EllucianApplication;
import com.ellucian.mobile.android.UICustomizer;
import com.ellucian.mobile.android.auth.LoginActivity;
import com.ellucian.mobile.android.auth.LoginUtil;


public class EventsDetailActivity extends ListActivity {

	private int position;
	private String moduleTitle;
	private String publicUrl;
	private String authenticatedUrl;
	
	private String chooseUrl() {
		final boolean loggedIn = LoginUtil.isLoggedIn(getApplicationContext());
		if (loggedIn) {
			return authenticatedUrl;
		} else {
			return publicUrl;
		}
	}
	
	private java.text.DateFormat dateFormat;
	private java.text.DateFormat timeFormat;
	private String title;
	private String jsonCalendar;

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.news_list);


		dateFormat = android.text.format.DateFormat
				.getDateFormat(getApplicationContext());
		timeFormat = android.text.format.DateFormat
				.getTimeFormat(getApplicationContext());

		

		final Intent intent = getIntent();
		position = intent.getIntExtra("position", 0);
		title = intent.getStringExtra("title");

		moduleTitle = intent.getStringExtra("moduleTitle");
		publicUrl = intent.getStringExtra("publicUrl");
		authenticatedUrl = intent.getStringExtra("authenticatedUrl");

		this.setTitle(title);
		UICustomizer.style(this);

		
		final String currentUrl = chooseUrl();

		final DataCache cache = ((EllucianApplication) getApplication())
				.getDataCache();

		final boolean current = cache.isCurrent(this, currentUrl);

		final String cachedContent = cache.getCache(this, currentUrl);

		if (cachedContent != null) {
			try {
				jsonCalendar = cachedContent;
				final Object o = cache.getCacheObject(currentUrl);
				if(o != null && o instanceof List) {
					calendars = (List<EventCalendar>) o;
				} else {
					calendars = EventsParser.parse(jsonCalendar);
					cache.putCacheObject(currentUrl, calendars);
				}
				calendars = (List<EventCalendar>) cache.getCacheObject(currentUrl);
				List<Event> items = calendars.get(position).items;

				setListAdapter(new EventsFeedAdapter(this, items, dateFormat, timeFormat));
				if(!current && calendars.size() != 1) {
					update();
				}
			} catch (final JSONException e) {
				Log.e(EllucianApplication.TAG, "Can't parse json in events");
			}
		} else if (!current) {
			update();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.events, menu);
		return true;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		final Event item = (Event) l.getItemAtPosition(position);

		final Intent intent = new Intent(EventsDetailActivity.this,
				EventContentActivity.class);
		intent.putExtra("title", item.getTitle());
		
		intent.putExtra("startDate", dateFormat.format(item.getStartDate().getTime()));
		intent.putExtra("endDate", dateFormat.format(item.getEndDate().getTime()));
		intent.putExtra("startDateLong",  item.getStartDate().getTime().getTime());
		intent.putExtra("endDateLong", item.getEndDate().getTime().getTime());
		
		if(!item.isAllDay()) {
			intent.putExtra("startTime", timeFormat.format(item.getStartDate().getTime()));
			intent.putExtra("endTime", timeFormat.format(item.getEndDate().getTime()));
		}
		intent.putExtra("location", item.getLocation());
		intent.putExtra("description", item.getDescription());
		intent.putExtra("allDay", item.isAllDay());
		startActivity(intent);

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final Intent data = new Intent();
		data.putExtra("position", position);

		switch (item.getItemId()) {
		case R.id.menu_refresh:
			update();
			break;
		case R.id.menu_login:
			if (calendars.size() == 1) {
				final Intent loginIntent = new Intent(
						EventsDetailActivity.this, LoginActivity.class);
				startActivityForResult(loginIntent, LOGIN_RESULT);
				// finish();
			} else {
				setResult(EventsActivity.CALENDAR_ACTIVITY_LOGIN_REQUESTED,
						data);
				finish();
			}
			break;
		case R.id.menu_logout:
			setResult(EventsActivity.CALENDAR_ACTIVITY_LOGOUT_REQUESTED, data);
			finish();
			break;
		}
		return false;
	}

	private static final int LOGIN_RESULT = RESULT_FIRST_USER;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == LOGIN_RESULT) {
			if (resultCode == RESULT_OK) {
				if (calendars.size() == 1) {
					final Intent intent = new Intent(EventsDetailActivity.this,
							EventsActivity.class);
					intent.putExtra("position", position);
					intent.putExtra("publicUrl", publicUrl);
					intent.putExtra("authenticatedUrl", authenticatedUrl);
					intent.putExtra("title", moduleTitle);
					startActivity(intent);
					finish();
				} else {
					data.putExtra("position", position);
					setResult(EventsActivity.CALENDAR_ACTIVITY_REFRESH, data);
					finish();
				}
			}
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_refresh).setEnabled(!refreshInProgress);
		final boolean loggedIn = LoginUtil.isLoggedIn(getApplicationContext());
		final boolean allowLogin = LoginUtil.allowLogin(getApplication());

		menu.findItem(R.id.menu_login).setVisible(!loggedIn && allowLogin);
		menu.findItem(R.id.menu_logout).setVisible(loggedIn && allowLogin);
		menu.findItem(R.id.menu_login).setEnabled(!refreshInProgress);
		menu.findItem(R.id.menu_logout).setEnabled(!refreshInProgress);
		return super.onPrepareOptionsMenu(menu);
	}
	private void update() {
		UICustomizer.setProgressBarVisible(EventsDetailActivity.this, true);
		final boolean loggedIn = LoginUtil.isLoggedIn(getApplicationContext());
		task = new UpdateEventsTask(this, loggedIn).execute(chooseUrl());

	}
	
	private AsyncTask<String, Void, List<EventCalendar>> task;
	private boolean refreshInProgress;

	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (task != null && refreshInProgress) {
			task.cancel(true);
		}
	}

	private class UpdateEventsTask extends
			AsyncTask<String, Void, List<EventCalendar>> {

		private final boolean authentication;
		private final Context context;
		 

		public UpdateEventsTask(Context context, boolean authentication) {
			this.authentication = authentication;
			this.context = context;
		}

		@Override
		protected List<EventCalendar> doInBackground(String... urls) {
			try {
				refreshInProgress = true;
				setResult(EventsActivity.CALENDAR_REDRAW_REQUESTED, new Intent());
				
				final HttpClient client = new DefaultHttpClient();
				final HttpGet request = new HttpGet();
				request.setURI(new URI(urls[0]));

				if (authentication) {
					final String username = LoginUtil.getUsername(context);
					final String password = LoginUtil.getPassword(context);

					request.addHeader(BasicScheme
							.authenticate(new UsernamePasswordCredentials(
									username, password), "UTF-8", false));
				}

				final HttpResponse response = client.execute(request);

				final int status = response.getStatusLine().getStatusCode();
				if (status == HttpStatus.SC_OK) {
					final BufferedReader in = new BufferedReader(
							new InputStreamReader(response.getEntity()
									.getContent(), "UTF-8"));
					final StringBuffer sb = new StringBuffer();
					String line = "";
					final String NL = System.getProperty("line.separator");
					while ((line = in.readLine()) != null) {
						sb.append(line + NL);
					}
					in.close();
					jsonCalendar = sb.toString();

					calendars = EventsParser.parse(jsonCalendar);
					if (authentication) {
						((EllucianApplication) getApplication()).getDataCache()
								.putAuthCache(urls[0], jsonCalendar, calendars);
					} else {
						((EllucianApplication) getApplication()).getDataCache()
								.putCache(urls[0], jsonCalendar, calendars);

					}
					return calendars;

				} else {
					throw new RuntimeException(response.getStatusLine()
							.toString());
				}

			} catch (final Exception e) {
				Log.e(EllucianApplication.TAG, "Events detail list update failed = " + e);
			}	
			return null;

		}

		@Override
		protected void onPostExecute(List<EventCalendar> calendars) {
			if(calendars != null && calendars.size() > position) {
				EventCalendar calendar = calendars.get(position);
				if(calendar.title.equals(title)) {
					List<Event> items = calendar.items;
					setListAdapter(new EventsFeedAdapter(EventsDetailActivity.this, items, dateFormat, timeFormat));
				} else {
					finish();
				}
			}
			refreshInProgress = false;
			UICustomizer.setProgressBarVisible(EventsDetailActivity.this, false);
		}
	}
	
	private List<EventCalendar> calendars;


}