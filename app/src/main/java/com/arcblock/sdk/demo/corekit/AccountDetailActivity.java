/*
 * Copyright (c) 2017-present ArcBlock Foundation Ltd <https://www.arcblock.io/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.arcblock.sdk.demo.corekit;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloCallback;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.fetcher.ApolloResponseFetchers;
import com.arcblock.sdk.demo.AccountByAddressQuery;
import com.arcblock.sdk.demo.DemoApplication;
import com.arcblock.sdk.demo.R;
import com.arcblock.sdk.demo.adapter.TsReceiverAdapter;
import com.arcblock.sdk.demo.adapter.TsSentAdapter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AccountDetailActivity extends AppCompatActivity {

	private static final String TAG = BlockDetailActivity.class.getSimpleName();
	public static final String ADDRESS_KEY = "address_key";
	private String address = "";
	private ApolloCall<AccountByAddressQuery.Data> accountByAddressCall;
	private Handler uiHandler = new Handler(Looper.getMainLooper());

	private TextView address_tv;
	private TextView balance_tv;
	private TextView sent_title_tv;
	private TextView receiver_title_tv;
	private ListView sent_lv;
	private ListView receive_lv;

	private boolean isSentLvShow = true;
	private boolean isReceiveLvShow = true;

	private TsSentAdapter mTsSentAdapter;
	private TsReceiverAdapter mTsReceiverAdapter;
	private List<AccountByAddressQuery.Datum1> sents = new ArrayList<>();
	private List<AccountByAddressQuery.Datum> receives = new ArrayList<>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_account_detail);

		address = getIntent().getExtras().getString(ADDRESS_KEY);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setTitle("Account-" + address);

		initView();

		address_tv.setText(address);

		fetchAccountByAddress();
	}

	private void initView() {
		address_tv = findViewById(R.id.address_tv);
		balance_tv = findViewById(R.id.balance_tv);
		sent_title_tv = findViewById(R.id.sent_title_tv);
		receiver_title_tv = findViewById(R.id.receiver_title_tv);
		sent_lv = findViewById(R.id.sent_lv);
		receive_lv = findViewById(R.id.receive_lv);

		mTsSentAdapter = new TsSentAdapter(this, R.layout.item_account_detail_transactions, sents);
		sent_lv.setAdapter(mTsSentAdapter);
		sent_lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < sents.size()) {
					Intent intent = new Intent(AccountDetailActivity.this, TransactionDetailActivity.class);
					Bundle bundle = new Bundle();
					bundle.putString(TransactionDetailActivity.TRANSACTION_HASH_KEY, sents.get(position).getHash());
					intent.putExtras(bundle);
					startActivity(intent);
				}
			}
		});
		mTsReceiverAdapter = new TsReceiverAdapter(this, R.layout.item_account_detail_transactions, receives);
		receive_lv.setAdapter(mTsReceiverAdapter);
		receive_lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < receives.size()) {
					Intent intent = new Intent(AccountDetailActivity.this, TransactionDetailActivity.class);
					Bundle bundle = new Bundle();
					bundle.putString(TransactionDetailActivity.TRANSACTION_HASH_KEY, receives.get(position).getHash());
					intent.putExtras(bundle);
					startActivity(intent);
				}
			}
		});

		refreshLvState();

		sent_title_tv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				isSentLvShow = !isSentLvShow;
				refreshLvState();
			}
		});

		receiver_title_tv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				isReceiveLvShow = !isReceiveLvShow;
				refreshLvState();
			}
		});
	}

	private void refreshLvState() {
		if (isSentLvShow) {
			sent_lv.setVisibility(View.VISIBLE);
		} else {
			sent_lv.setVisibility(View.GONE);
		}
		if (isReceiveLvShow) {
			receive_lv.setVisibility(View.VISIBLE);
		} else {
			receive_lv.setVisibility(View.GONE);
		}
	}

	private ApolloCall.Callback<AccountByAddressQuery.Data> dataCallback
			= new ApolloCallback<>(new ApolloCall.Callback<AccountByAddressQuery.Data>() {
		@Override
		public void onResponse(@NotNull Response<AccountByAddressQuery.Data> response) {
			if (response != null && response.data() != null && response.data().getAccountByAddress() != null) {
				AccountByAddressQuery.AccountByAddress accountByAddress = response.data().getAccountByAddress();
				balance_tv.setText(accountByAddress.getBalance() == null ? "0 BTC" : accountByAddress.getBalance() + " BTC");
				if (accountByAddress.getTxsSent()!=null&&accountByAddress.getTxsSent().getData()!=null) {
					sents.clear();
					sents.addAll(accountByAddress.getTxsSent().getData());
					mTsSentAdapter.notifyDataSetChanged();
				}
				if (accountByAddress.getTxsReceived()!=null&&accountByAddress.getTxsReceived().getData()!=null) {
					receives.clear();
					receives.addAll(accountByAddress.getTxsReceived().getData());
					mTsReceiverAdapter.notifyDataSetChanged();
				}
			}
		}

		@Override
		public void onFailure(@NotNull ApolloException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}, uiHandler);

	private void fetchAccountByAddress() {
		AccountByAddressQuery transactionByHashQuery = AccountByAddressQuery.builder()
				.address(address)
				.build();
		accountByAddressCall = DemoApplication.getInstance().abCoreKitClient()
				.query(transactionByHashQuery)
				.responseFetcher(ApolloResponseFetchers.NETWORK_FIRST);
		accountByAddressCall.enqueue(dataCallback);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home://返回键的id
				this.finish();
				return false;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (accountByAddressCall != null) {
			accountByAddressCall.cancel();
		}
	}

}