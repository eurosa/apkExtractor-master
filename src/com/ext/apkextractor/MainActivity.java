package com.ext.apkextractor;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import axp.tool.apkextractor.R;

public class MainActivity extends AppCompatActivity {
	private ApkListAdapter apkListAdapter;

	private ProgressBar progressBar;
	private PermissionResolver permissionResolver;
	private AdView mAdView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);


        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));


		RecyclerView listView = findViewById(android.R.id.list);





	apkListAdapter = new ApkListAdapter(this);
		listView.setLayoutManager(new LinearLayoutManager(this));
		listView.setAdapter(apkListAdapter);

		progressBar = findViewById(android.R.id.progress);
		progressBar.setVisibility(View.VISIBLE);

		new Loader(this).execute();

		permissionResolver = new PermissionResolver(this);
	}



	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (!permissionResolver.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	public void hideProgressBar() {
		progressBar.setVisibility(View.GONE);
	}

	public void addItem(PackageInfo item) {
		apkListAdapter.addItem(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);

		SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
		final SearchView searchView = (SearchView)MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
		searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
			@SuppressLint("RestrictedApi")
			@Override
			public void onFocusChange(View view, boolean queryTextFocused) {
				if (!queryTextFocused && searchView.getQuery().length() < 1) {
					getSupportActionBar().collapseActionView();
				}
			}
		});
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String s) {
				return false;
			}

			@Override
			public boolean onQueryTextChange(String s) {
				apkListAdapter.setSearchPattern(s);
				return true;
			}
		});

		return super.onCreateOptionsMenu(menu);
	}

	public void doExctract(final PackageInfo info) {
		if (!permissionResolver.resolve()) return;

		final Extractor extractor = new Extractor();
		try {
			String dst = extractor.extractWithoutRoot(info);
			shareApplication(dst);
			Toast.makeText(this, String.format(this.getString(R.string.toast_extracted), dst), Toast.LENGTH_SHORT).show();
			return;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		new AlertDialog.Builder(this)
			.setTitle(R.string.alert_root_title)
			.setMessage(R.string.alert_root_body)
			.setPositiveButton(R.string.alert_root_yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						String dst = extractor.extractWithRoot(info);

						shareApplication(dst);

						Toast.makeText(MainActivity.this, String.format(MainActivity.this.getString(R.string.toast_extracted), dst), Toast.LENGTH_SHORT).show();
					} catch (Exception e) {
						e.printStackTrace();
						Toast.makeText(MainActivity.this, R.string.toast_failed, Toast.LENGTH_SHORT).show();
					}
				}
			}).setNegativeButton(R.string.alert_root_no, null)
			.show();
	}


	private void shareApplication(String dst) {
		ApplicationInfo app = getApplicationContext().getApplicationInfo();
		String filePath = dst;

		Intent intent = new Intent(Intent.ACTION_SEND);

		// MIME of .apk is "application/vnd.android.package-archive".
		// but Bluetooth does not accept this. Let's use "*/*" instead.
		intent.setType("*/*");

		// Append file and send Intent
		File originalApk = new File(filePath);

		try {
			//Make new directory in new location
			File tempFile = new File(getExternalCacheDir() + "/ExtractedApk");
			//If directory doesn't exists create new
			if (!tempFile.isDirectory())
				if (!tempFile.mkdirs())
					return;
			//Get application's name and convert to lowercase
			tempFile = new File(tempFile.getPath() + "/" + getString(app.labelRes).replace(" ","").toLowerCase() + ".apk");
			//If file doesn't exists create new
			if (!tempFile.exists()) {
				if (!tempFile.createNewFile()) {
					return;
				}
			}
			//Copy file to new location
			InputStream in = new FileInputStream(originalApk);
			OutputStream out = new FileOutputStream(tempFile);

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
			System.out.println("File copied.");
			//Open share dialog
			intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tempFile));
			startActivity(Intent.createChooser(intent, "Share app via"));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	class Loader extends AsyncTask<Void, PackageInfo, Void> {
		ProgressDialog dialog;
		MainActivity   mainActivity;

		public Loader(MainActivity a) {
			dialog = ProgressDialog.show(a, getString(R.string.dlg_loading_title), getString(R.string.dlg_loading_body));
			mainActivity = a;
		}

		@Override
		protected Void doInBackground(Void... params) {
			List<PackageInfo> packages = getPackageManager().getInstalledPackages(PackageManager.GET_META_DATA);
			for (PackageInfo packageInfo : packages) {
				publishProgress(packageInfo);
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(PackageInfo... values) {
			super.onProgressUpdate(values);
			mainActivity.addItem(values[0]);
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			super.onPostExecute(aVoid);
			dialog.dismiss();
		}
	}
}
