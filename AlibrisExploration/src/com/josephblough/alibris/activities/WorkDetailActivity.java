package com.josephblough.alibris.activities;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.josephblough.alibris.ApplicationController;
import com.josephblough.alibris.R;
import com.josephblough.alibris.adapters.SearchResultAdapter;
import com.josephblough.alibris.data.ItemSearchResult;
import com.josephblough.alibris.data.ReviewCollection;
import com.josephblough.alibris.data.WorkSearchResult;
import com.josephblough.alibris.tasks.DataReceiver;
import com.josephblough.alibris.tasks.RecommendationRetrieverTask;
import com.josephblough.alibris.tasks.ReviewRetrieverTask;
import com.josephblough.alibris.tasks.SearchResultsRetrieverTask;
import com.josephblough.alibris.transport.SearchRequestConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

public class WorkDetailActivity extends Activity implements OnItemClickListener {

    private static final String TAG = "WorkDetailActivity";
    
    public static final String WORK_AS_JSON = "WorkDetailActivity.work_as_json";

    private static final String MIN_PRICE_KEY = "min.price";
    private static final String JSON_REVIEWS__KEY = "json.reviews";
    private static final String JSON_RECOMMENDATIONS__KEY = "json.recommendations";
    private Double asyncMinPrice = null;
    private String jsonReviews = null;
    private String jsonRecommendations = null;

    private WorkSearchResult work;
    
    private Handler supplementalRetrievalsHandler = null;
    private Thread reviewThread = null;
    private Thread recommendationThread = null;
    
    Button retryReviewsButton;
    Button retryRecommendationsButton;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.item_details);
        
        final ApplicationController app = (ApplicationController) getApplication();
        app.initAlibrisHeader(this);
        
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(MIN_PRICE_KEY))
        	asyncMinPrice = savedInstanceState.getDouble(MIN_PRICE_KEY);
            if (savedInstanceState.containsKey(JSON_REVIEWS__KEY))
        	jsonReviews = savedInstanceState.getString(JSON_REVIEWS__KEY);
            if (savedInstanceState.containsKey(JSON_RECOMMENDATIONS__KEY))
        	jsonRecommendations = savedInstanceState.getString(JSON_RECOMMENDATIONS__KEY);
        }
        
        final String json = getIntent().getStringExtra(WORK_AS_JSON);
        if (json != null) {
            try {
        	work = new WorkSearchResult(new JSONObject(json));
        	Log.d(TAG, "Read in work " + work.workId);
        	
        	if (work.workId > 0) {
        	    populateWorkDetails();

		    if (work.minPrice == null) {
			if (asyncMinPrice != null)
			    work.minPrice = asyncMinPrice;
			else
			    loadPrice();
		    }
        	    
        	    // Alibris API only allows for 2 calls per second.
        	    reviewThread = new Thread(new Runnable() {

        		public void run() {
        		    loadReviews();
        		}
        	    });
        	    
        	    recommendationThread = new Thread(new Runnable() {

        		public void run() {
        		    loadRecommendations();
        		}
        	    });

        	    supplementalRetrievalsHandler = new Handler();
        	    int delay = 1000;
        	    if (jsonReviews != null) {
        		new ReviewsDataReceiver().dataReceived(new JSONObject(jsonReviews));
        	    }
        	    else {
        		supplementalRetrievalsHandler.postDelayed(reviewThread, delay);
        		// add 1 second to the delay
        		delay += 1000;
        	    }
        	    
        	    if (jsonRecommendations != null) {
        		new RecommendationsDataReceiver().dataReceived(new JSONObject(jsonRecommendations));
        	    }
        	    else {
        		supplementalRetrievalsHandler.postDelayed(recommendationThread, delay);
        	    }
        	}
            }
            catch (JSONException e) {
        	Log.e(TAG, e.getMessage(), e);
            }
        }
        
	retryReviewsButton = (Button)findViewById(R.id.item_details_retry_reviews_button);
	retryReviewsButton.setOnClickListener(new OnClickListener() {
	    
	    public void onClick(View v) {
		retryReviewsButton.setVisibility(View.GONE);
		loadReviews();
	    }
	});
	
	retryRecommendationsButton = (Button)findViewById(R.id.item_details_retry_recommendations_button);
	retryRecommendationsButton.setOnClickListener(new OnClickListener() {
	    
	    public void onClick(View v) {
		retryRecommendationsButton.setVisibility(View.GONE);
		loadRecommendations();
	    }
	});
    }
    
    private synchronized void populateWorkDetails() {
	ImageView image = (ImageView) findViewById(R.id.item_details_image);
	image.setTag(work.imageURL);
	//Log.d(TAG, "Displaying image " + image.getTag());
	
	final ApplicationController app = (ApplicationController) getApplicationContext();
	app.imageLoader.displayImage(work.imageURL, image);
	
	image.setOnClickListener(new OnClickListener() {
	    
	    public void onClick(View v) {
		AlertDialog.Builder builder = new AlertDialog.Builder(WorkDetailActivity.this);

		builder.setTitle("Image Close-up");
		ImageView tempImage = new ImageView(WorkDetailActivity.this);
		app.imageLoader.displayImage(work.imageURL, tempImage);
		builder.setView(tempImage);
		builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int whichButton) {
			// Canceled.
			dialog.cancel();
		    }
		});
		
		builder.show();
	    }
	});
	
	((TextView) findViewById(R.id.item_details_title)).setText(work.title);
	((TextView) findViewById(R.id.item_details_author)).setText(work.author);
	if (work.minPrice != null)
	    ((TextView) findViewById(R.id.item_details_min_price)).setText(NumberFormat.getCurrencyInstance().format(work.minPrice));
	else
	    ((TextView) findViewById(R.id.item_details_min_price)).setText("");
	((TextView) findViewById(R.id.item_details_available)).setText(Integer.toString(work.quantityAvailable));
	((TextView) findViewById(R.id.item_details_synopsis)).setText(Html.fromHtml(work.synopsis));
	
	((Button) findViewById(R.id.item_details_see_all_offers)).setOnClickListener(new OnClickListener() {
	    
	    public void onClick(View v) {
		Intent intent = new Intent(WorkDetailActivity.this, WorkOffersActivity.class);
		intent.putExtra(WorkOffersActivity.WORK_ID, work.workId);
		startActivity(intent);
	    }
	});
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ListView recommendationList = (ListView)findViewById(R.id.item_details_recommendations_list);
	Log.d(TAG, "Clicked on " + recommendationList.getAdapter().getItemId(position) + ", position " + position);
	final String workAsJson = ((SearchResultAdapter)recommendationList.getAdapter()).getItem(position).toString();
	
	Intent intent = new Intent(this, WorkDetailActivity.class);
	intent.putExtra(WorkDetailActivity.WORK_AS_JSON, workAsJson);
	startActivity(intent);
    }

    private void loadPrice() {
	loadPrice(false);
    }
    
    @SuppressWarnings("unchecked")
    private void loadPrice(final boolean wait) {
	Log.d(TAG, "loadPrice");
	
	// Do an item search giving work id, sorting by price, and only getting a record count of 1 record
	SearchResultsRetrieverTask retriever = new SearchResultsRetrieverTask(new DataReceiver() {

	    public void error(String error) {
		Toast.makeText(WorkDetailActivity.this, "Error retrieving lowest price", Toast.LENGTH_SHORT).show();
	    }

	    public void dataReceived(JSONObject data) {
		try {
		    JSONArray works = data.getJSONArray("book");
		    int length = works.length();
		    if (length > 0) {
			ItemSearchResult result = new ItemSearchResult(works.getJSONObject(0));
			work.minPrice = result.price;
			((TextView) findViewById(R.id.item_details_min_price)).setText(NumberFormat.getCurrencyInstance().format(result.price));
		    }
		}
		catch (JSONException e) {
		    Log.e(TAG, e.getMessage(), e);
		}
	    }
	});

	Map<String, String> params = new HashMap<String, String>();
	params.put(SearchRequestConstants.ITEMS_SEARCH_FIELD_WORK_ID, Integer.toString(work.workId));
	params.put(SearchRequestConstants.SEARCH_SORT, SearchRequestConstants.SORT_PRICE);
	params.put(SearchRequestConstants.SEARCH_RESULTS_COUNT, "1");
	retriever.execute(params);
	
	if (wait) {
	    try {
		retriever.get();
	    }
	    catch (ExecutionException e) {
		Log.e(TAG, e.getMessage(), e);
	    }
	    catch (InterruptedException e) {
		Log.e(TAG, e.getMessage(), e);
	    }
	}
    }
    
    private void loadReviews() {
	loadReviews(false);
    }
    
    private void loadReviews(final boolean wait) {
	//Log.d(TAG, "loadReviews");
	
	findViewById(R.id.item_detail_reviews_busy_indicator).setVisibility(View.VISIBLE);
	
	ReviewRetrieverTask reviewRetriever = new ReviewRetrieverTask(new ReviewsDataReceiver());
	reviewRetriever.execute(work.workId);
	if (wait) {
	    try {
		reviewRetriever.get();
	    }
	    catch (ExecutionException e) {
		Log.e(TAG, e.getMessage(), e);
	    }
	    catch (InterruptedException e) {
		Log.e(TAG, e.getMessage(), e);
	    }
	}
    }
    
    private void loadRecommendations() {
	loadRecommendations(false);
    }
    
    private void loadRecommendations(final boolean wait) {
	//Log.d(TAG, "loadRecommendations");
	
	findViewById(R.id.item_detail_recommendations_busy_indicator).setVisibility(View.VISIBLE);
	
	RecommendationRetrieverTask recommendationRetriever = new RecommendationRetrieverTask(new RecommendationsDataReceiver());
	recommendationRetriever.execute(work.workId);
	if (wait) {
	    try {
		recommendationRetriever.get();
	    }
	    catch (ExecutionException e) {
		Log.e(TAG, e.getMessage(), e);
	    }
	    catch (InterruptedException e) {
		Log.e(TAG, e.getMessage(), e);
	    }
	}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (asyncMinPrice != null)
            outState.putDouble(MIN_PRICE_KEY, asyncMinPrice);
        
        if (jsonReviews != null)
            outState.putString(JSON_REVIEWS__KEY, jsonReviews);
        
        if (jsonRecommendations != null)
            outState.putString(JSON_RECOMMENDATIONS__KEY, jsonRecommendations);
    }

    private class ReviewsDataReceiver implements DataReceiver {

	public void error(String error) {
	    findViewById(R.id.item_detail_reviews_busy_indicator).setVisibility(View.GONE);
	    retryReviewsButton.setVisibility(View.VISIBLE);
	    Toast.makeText(WorkDetailActivity.this, error, Toast.LENGTH_SHORT).show();
	}

	public void dataReceived(final JSONObject data) {
	    findViewById(R.id.item_detail_reviews_busy_indicator).setVisibility(View.GONE);
	    
	    jsonReviews = data.toString();
	    ReviewCollection reviews = new ReviewCollection(data);
	    TextView ratingLabel = (TextView)findViewById(R.id.item_details_overall_rating_label);
	    RatingBar ratingBar = (RatingBar)findViewById(R.id.item_details_overall_rating);
	    Button seeReviewsButton = (Button)findViewById(R.id.item_details_reviews_button);
	    if (reviews.getReviews() != null && reviews.getReviews().size() > 0) {
		Log.d(TAG, "Setting rating to " + reviews.overallRating + " for " + 
			reviews.getReviews().size() + " reviews");
		ratingBar.setRating((float)reviews.overallRating);

		ratingLabel.setText("Overall rating: " + Double.toString(reviews.overallRating));

		seeReviewsButton.setOnClickListener(new OnClickListener() {
		    public void onClick(View v) {
			Intent intent = new Intent(WorkDetailActivity.this, WorkReviewsActivity.class);
			intent.putExtra(WorkReviewsActivity.REVIEWS_AS_JSON, data.toString());
			startActivity(intent);
		    }
		});

		ratingLabel.setVisibility(View.VISIBLE);
		ratingBar.setVisibility(View.VISIBLE);
		seeReviewsButton.setVisibility(View.VISIBLE);
	    }
	    else {
		ratingLabel.setText("No reviews at this time");

		ratingLabel.setVisibility(View.VISIBLE);
		ratingBar.setVisibility(View.GONE);
		seeReviewsButton.setVisibility(View.GONE);
	    }
	}
    }
    
    private class RecommendationsDataReceiver implements DataReceiver {
	public void error(String error) {
	    findViewById(R.id.item_detail_recommendations_busy_indicator).setVisibility(View.GONE);
	    retryRecommendationsButton.setVisibility(View.VISIBLE);
	    Toast.makeText(WorkDetailActivity.this, error, Toast.LENGTH_SHORT).show();
	}

	public void dataReceived(JSONObject data) {
	    findViewById(R.id.item_detail_recommendations_busy_indicator).setVisibility(View.GONE);

	    ListView recommendationList = (ListView)findViewById(R.id.item_details_recommendations_list);
	    recommendationList.setOnItemClickListener(WorkDetailActivity.this);
	    try {
		jsonRecommendations = data.toString();
		JSONArray works = data.getJSONArray("work");
		int length = works.length();
		List<WorkSearchResult> results = new ArrayList<WorkSearchResult>();
		for (int i=0; i<length; i++) {
		    results.add(new WorkSearchResult(works.getJSONObject(i)));
		}

		SearchResultAdapter adapter = new SearchResultAdapter(WorkDetailActivity.this, results);
		recommendationList.setAdapter(adapter);
		recommendationList.setVisibility(View.VISIBLE);
	    }
	    catch (JSONException e) {
		recommendationList.setVisibility(View.GONE);
		Log.e(TAG, e.getMessage(), e);
	    }
	}
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        /*if (supplementalRetrievalsHandler != null) {
            if (reviewThread != null)
        	supplementalRetrievalsHandler.removeCallbacks(reviewThread);
            if (recommendationThread != null)
        	supplementalRetrievalsHandler.removeCallbacks(recommendationThread);
        }*/
    }
}
