package com.vortexwolf.dvach.activities.threads;

import java.util.ArrayList;

import com.vortexwolf.dvach.R;
import com.vortexwolf.dvach.activities.addpost.AddPostActivity;
import com.vortexwolf.dvach.activities.boards.PickBoardActivity;
import com.vortexwolf.dvach.activities.browser.BrowserLauncher;
import com.vortexwolf.dvach.activities.posts.DownloadPostsTask;
import com.vortexwolf.dvach.activities.posts.PostsListActivity;
import com.vortexwolf.dvach.api.entities.ThreadInfo;
import com.vortexwolf.dvach.api.entities.ThreadsList;
import com.vortexwolf.dvach.common.Constants;
import com.vortexwolf.dvach.common.MainApplication;
import com.vortexwolf.dvach.common.http.DownloadFileTask;
import com.vortexwolf.dvach.common.http.HttpBitmapReader;
import com.vortexwolf.dvach.common.library.BitmapManager;
import com.vortexwolf.dvach.common.library.MyLog;
import com.vortexwolf.dvach.common.library.Tracker;
import com.vortexwolf.dvach.common.utils.AppearanceUtils;
import com.vortexwolf.dvach.common.utils.StringUtils;
import com.vortexwolf.dvach.common.utils.ThreadPostUtils;
import com.vortexwolf.dvach.common.utils.UriUtils;
import com.vortexwolf.dvach.interfaces.IBitmapManager;
import com.vortexwolf.dvach.interfaces.IDownloadFileService;
import com.vortexwolf.dvach.interfaces.IJsonApiReader;
import com.vortexwolf.dvach.interfaces.IListView;
import com.vortexwolf.dvach.interfaces.IThumbnailOnClickListenerFactory;
import com.vortexwolf.dvach.presentation.models.AttachmentInfo;
import com.vortexwolf.dvach.presentation.models.ThreadItemViewModel;
import com.vortexwolf.dvach.settings.ApplicationPreferencesActivity;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

public class ThreadsListActivity extends ListActivity implements IListView<ThreadsList> {
    private static final String TAG = "ThreadsListActivity";

    private MainApplication mApplication;
    private IBitmapManager mBitmapManager;
	private IJsonApiReader mJsonReader;
	private IDownloadFileService mDownloadFileService;
	private Tracker mTracker;
	
	private final IThumbnailOnClickListenerFactory mThumbnailOnClickListenerFactory = new ThumbnailOnClickListenerFactory();
	private DownloadThreadsTask mCurrentDownloadTask = null;
	private ThreadsListAdapter mAdapter = null;
	
	private int mCurrentTheme;
	private boolean mCurrentDisplayNavigationBar;
	private boolean mCurrentLoadThumbnails;
	
	private View mLoadingView = null;
	private View mErrorView = null;
	private enum ViewType { LIST, LOADING, ERROR};
	private ViewType mCurrentView = null;
	private View mNavigationBar;
	
	private String mBoardName;
	private int mPageNumber = 0;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        this.mApplication = (MainApplication) this.getApplication();
        this.mJsonReader = this.mApplication.getJsonApiReader();
        this.mBitmapManager = new BitmapManager(new HttpBitmapReader(this.mApplication.getHttpClient()));
        this.mDownloadFileService = this.mApplication.getDownloadFileService();
        this.mCurrentTheme = this.mApplication.getSettings().getTheme();
        this.mCurrentDisplayNavigationBar = this.mApplication.getSettings().isDisplayNavigationBar();
        this.mCurrentLoadThumbnails = this.mApplication.getSettings().isLoadThumbnails();
        this.mTracker = this.mApplication.getTracker();
        
    	Uri data = this.getIntent().getData();
    	if(data != null){
    		mBoardName = UriUtils.getBoardName(data);
    		mPageNumber = UriUtils.getBoardPageNumber(data);
    	}
    	
    	if(StringUtils.isEmpty(mBoardName)){
    		mBoardName = this.mApplication.getSettings().getHomepage();
    	}
    	
        this.resetUI();

        if(mAdapter == null)
        {       	
        	mAdapter = new ThreadsListAdapter(this, mBoardName, mBitmapManager, mThumbnailOnClickListenerFactory);
	        setListAdapter(mAdapter);
	        
	        this.refreshThreads();
        }
        
        String pageTitle = mPageNumber != 0 
				        ? String.format(getString(R.string.data_board_title_with_page), mBoardName, mPageNumber) 
				        : String.format(getString(R.string.data_board_title), mBoardName);
		this.setTitle(pageTitle);
		
        this.mTracker.setBoardVar(mBoardName);
        this.mTracker.setPageNumberVar(mPageNumber);
        this.mTracker.trackActivityView(TAG);
    }
    
	@Override
	protected void onDestroy() {
		this.mBitmapManager.clearCache();
		
		if(this.mCurrentDownloadTask != null){
			this.mCurrentDownloadTask.cancel(true);
		}
		
		MyLog.d(TAG, "Detstroyed");
		
		super.onDestroy();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		int newTheme = this.mApplication.getSettings().getTheme();
		if(this.mCurrentTheme != newTheme){
			this.mCurrentTheme = newTheme;
			this.resetUI();
			return;
		}
		
		boolean newDisplayNavigationBar = this.mApplication.getSettings().isDisplayNavigationBar();
		if(this.mCurrentDisplayNavigationBar != newDisplayNavigationBar){
			this.mCurrentDisplayNavigationBar = newDisplayNavigationBar;
			
			this.mNavigationBar.setVisibility(this.mCurrentDisplayNavigationBar ? View.VISIBLE : View.GONE);
		}
		
		boolean newLoadThumbnails = this.mApplication.getSettings().isLoadThumbnails();
		if(this.mCurrentLoadThumbnails != newLoadThumbnails){
			this.mCurrentLoadThumbnails = newLoadThumbnails;
			this.mAdapter.notifyDataSetChanged();
		}
	}
	
	private void resetUI()
    {
		// Возвращаем прежнее положение scroll view после перезагрузки темы
		AppearanceUtils.ListViewPosition position = AppearanceUtils.getCurrentListPosition(this.getListView());

		this.setTheme(this.mCurrentTheme);
        this.setContentView(R.layout.threads_list_view);

        this.mLoadingView = this.findViewById(R.id.loadingView);
        this.mErrorView = this.findViewById(R.id.error);
        
        this.registerForContextMenu(this.getListView());
        this.getListView().setSelectionFromTop(position.position, position.top);
        
        // Отображаем или список, или индикатор ошибки, или загрузку в новой теме
        if(this.mCurrentView != null){
        	this.switchToView(this.mCurrentView);
        }
        
        //Панель навигации по страницам
        this.mNavigationBar = this.findViewById(R.id.threads_navigation_bar);
        this.mNavigationBar.setVisibility(this.mCurrentDisplayNavigationBar ? View.VISIBLE : View.GONE);
        
        TextView pageNumberView = (TextView)this.findViewById(R.id.threads_page_number);
        pageNumberView.setText(String.valueOf(this.mPageNumber));
        
        ImageButton nextButton = (ImageButton)this.findViewById(R.id.threads_next_page);
        ImageButton prevButton = (ImageButton)this.findViewById(R.id.threads_prev_page);
        if(this.mPageNumber == 0){
        	prevButton.setVisibility(View.GONE);
        }
        else if(this.mPageNumber == 19){
        	nextButton.setVisibility(View.GONE);
        }
        
        prevButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ThreadsListActivity.this.navigateToBoardPageNumber(mBoardName, mPageNumber - 1);
			}
		});
        
        nextButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ThreadsListActivity.this.navigateToBoardPageNumber(mBoardName, mPageNumber + 1);
			}
		});
       
    }

	@Override
	public void setData(ThreadsList threads) {
		if(threads != null){
			this.mAdapter.setAdapterData(threads.getThreads());
		}
		else {
			MyLog.e(TAG, "threads = null");
		}
	}
	
	@Override
    public void showLoadingScreen() {
		this.switchToView(ViewType.LOADING);
    }
	
	@Override
    public void hideLoadingScreen() {
    	this.switchToView(ViewType.LIST);
    	this.mCurrentDownloadTask = null;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.board, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.pick_board_menu_id:
    		//Start new activity
    		Intent pickBoardIntent = new Intent(getApplicationContext(), PickBoardActivity.class);
    		startActivityForResult(pickBoardIntent, Constants.REQUEST_CODE_PICK_BOARD_ACTIVITY);
    		break;
    	case R.id.refresh_menu_id:
    		this.refreshThreads();
    		break;
    	case R.id.open_browser_menu_id:
    		BrowserLauncher.launchExternalBrowser(this, UriUtils.create2chURL(this.mBoardName, this.mPageNumber), true);
    		break;
    	case R.id.preferences_menu_id:
    		//Start new activity
    		Intent preferencesIntent = new Intent(getApplicationContext(), ApplicationPreferencesActivity.class);
    		startActivity(preferencesIntent);
    		break;
    	case R.id.add_menu_id:
    		this.navigateToAddThreadView();
    		break;
    	}
    	
    	return true;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	if (resultCode == Activity.RESULT_OK) {
	    	switch(requestCode) {
		    	case Constants.REQUEST_CODE_PICK_BOARD_ACTIVITY:
		    	    	String boardCode = intent.getExtras().getString(Constants.EXTRA_SELECTED_BOARD);
		    			//Открываем новую борду, если не совпадает с открытой
		    	    	if (!StringUtils.isEmpty(boardCode) && !mBoardName.equals(boardCode)) {
		    	    		this.navigateToBoardPageNumber(boardCode, 0);
		    			}
		    		break;
		    	case Constants.REQUEST_CODE_ADD_POST_ACTIVITY:
		    		// Получаем номер созданного треда и переходим к нему. Иначе обновляем список тредов на всякий случай
		    		String redirectedThreadNumber = intent.getExtras().getString(Constants.EXTRA_REDIRECTED_THREAD_NUMBER);
		    		if(redirectedThreadNumber != null){
		    			this.navigateToThread(redirectedThreadNumber);
		    		}
		    		else{
		    			this.refreshThreads();
		    		}
					break;
	    	}
    	}
    }

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		if(mAdapter == null) return;
		
		ThreadItemViewModel info = mAdapter.getItem(position);
		
		this.navigateToThread(info.getNumber(), info.getSpannedSubject() != null ? info.getSpannedSubject().toString() : null);
	}

	@Override
	public void setWindowProgress(int value) {
		this.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, value);
	}

	//При повороте экрана из списка заголовки исчезают, поэтому попробую такой костыль
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      this.setListAdapter(this.mAdapter);
    }

	@Override
	public void showError(String error) {
		this.switchToView(ViewType.ERROR);
		
		TextView errorTextView = (TextView)mErrorView.findViewById(R.id.error_text);
		if(errorTextView != null){
			errorTextView.setText(error != null ? error : this.mApplication.getErrors().getUnknownError());
		}
	}

	private void switchToView(ViewType vt){
		this.mCurrentView = vt;
		
		switch(vt){
			case LIST:
				this.getListView().setVisibility(View.VISIBLE);
				mLoadingView.setVisibility(View.GONE);
				mErrorView.setVisibility(View.GONE);
				break;
			case LOADING:
				this.getListView().setVisibility(View.GONE);
				mLoadingView.setVisibility(View.VISIBLE);
				mErrorView.setVisibility(View.GONE);
				break;
			case ERROR:
				this.getListView().setVisibility(View.GONE);
				mLoadingView.setVisibility(View.GONE);
				mErrorView.setVisibility(View.VISIBLE);
				break;
		}
	}
	
	@Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo){
    	super.onCreateContextMenu(menu, v, menuInfo);
    	
    	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
    	ThreadItemViewModel item = mAdapter.getItem(info.position);
    	
    	menu.add(Menu.NONE, Constants.CONTEXT_MENU_ANSWER, 0, this.getString(R.string.cmenu_answer_without_reading));
    	if(item.hasAttachment()){
    		menu.add(Menu.NONE, Constants.CONTEXT_MENU_OPEN_ATTACHMENT, 1, this.getString(R.string.cmenu_open_attachment));
    	}
    	if(item.hasAttachment() && item.getAttachment(this.mBoardName).isFile()){
    		menu.add(Menu.NONE, Constants.CONTEXT_MENU_DOWNLOAD_FILE, 2, this.getString(R.string.cmenu_download_file));
    	}
	}
	
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
    	ThreadItemViewModel info = mAdapter.getItem(menuInfo.position);
        
        switch(item.getItemId()){
	        case Constants.CONTEXT_MENU_ANSWER: {
		        Intent addPostIntent = new Intent(getApplicationContext(), AddPostActivity.class);
	    		addPostIntent.putExtra(Constants.EXTRA_BOARD_NAME, this.mBoardName);
	    		addPostIntent.putExtra(Constants.EXTRA_THREAD_NUMBER, info.getNumber());
				startActivity(addPostIntent);
				return true;
	        }
	        case Constants.CONTEXT_MENU_OPEN_ATTACHMENT: {
	        	AttachmentInfo attach = info.getAttachment(this.mBoardName);
	        	if(attach != null && !attach.isEmpty()){
	        		this.mThumbnailOnClickListenerFactory.raiseClick(attach.getSourceUrl(), this, attach.getSize());
	        		return true;
	        	}
	        	return false;
	        }
	        case Constants.CONTEXT_MENU_DOWNLOAD_FILE: {
	        	this.mDownloadFileService.downloadFile(this, info.getAttachment(this.mBoardName).getSourceUrl());
	        	
	    	    this.mTracker.trackEvent(Tracker.CATEGORY_UI, Tracker.ACTION_DOWNLOAD_FILE, Tracker.LABEL_DOWNLOAD_FILE_FROM_CONTEXT_MENU);
	    	    
	        	return true;
	        }
        }
        
        return false;
    }
    
    private void navigateToAddThreadView(){
    	Intent addPostIntent = new Intent(this.getApplicationContext(), AddPostActivity.class);
		addPostIntent.putExtra(Constants.EXTRA_BOARD_NAME, this.mBoardName);
		addPostIntent.putExtra(Constants.EXTRA_THREAD_NUMBER, Constants.ADD_THREAD_PARENT);

		startActivityForResult(addPostIntent, Constants.REQUEST_CODE_ADD_POST_ACTIVITY);
    }
    
    private void navigateToThread(String threadNumber){
    	navigateToThread(threadNumber, null);
    }
    
    private void navigateToThread(String threadNumber, String threadSubject){
		Intent i = new Intent(this.getApplicationContext(), PostsListActivity.class);
		i.putExtra(Constants.EXTRA_BOARD_NAME, mBoardName);
		i.putExtra(Constants.EXTRA_THREAD_NUMBER, threadNumber);
		if(threadSubject != null){
			i.putExtra(Constants.EXTRA_THREAD_SUBJECT, threadSubject);
		}
		
		startActivity(i);
    }
    
    private void navigateToBoardPageNumber(String boardCode, int pageNumber){
    	Intent i = new Intent(this.getApplicationContext(), ThreadsListActivity.class);
		i.setData(Uri.parse(UriUtils.create2chURL(boardCode, pageNumber)));
		
		startActivity(i);
    }
     
	private void refreshThreads(){
		if(this.mCurrentDownloadTask != null){
			this.mCurrentDownloadTask.cancel(true);
		}
		
		if(this.mBoardName != null){
			this.mCurrentDownloadTask = new DownloadThreadsTask(this, this, this.mBoardName, this.mPageNumber, this.mJsonReader);
			this.mCurrentDownloadTask.execute();
		}
	}
}