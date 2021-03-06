package com.arcblock.corekit.viewmodel;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import com.apollographql.apollo.subscription.OperationClientMessage;
import com.arcblock.corekit.ABCoreKitClient;
import com.arcblock.corekit.bean.CoreKitBean;
import com.arcblock.corekit.socket.Binding;
import com.arcblock.corekit.socket.Channel;
import com.arcblock.corekit.socket.ChannelState;
import com.arcblock.corekit.socket.CoreKitMsgBean;
import com.arcblock.corekit.socket.CoreKitSocketStatusCallBack;
import com.arcblock.corekit.socket.IErrorCallback;
import com.arcblock.corekit.socket.IMessageCallback;
import com.arcblock.corekit.socket.ISocketCloseCallback;
import com.arcblock.corekit.socket.ISocketOpenCallback;
import com.arcblock.corekit.utils.CoreKitLogUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;

import org.json.JSONObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.ref.WeakReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class CoreKitSubViewModel<T, D extends com.apollographql.apollo.api.Subscription> extends ViewModel {


	public static CoreKitSubViewModel getInstance(FragmentActivity activity, CustomClientFactory factory) {
		return ViewModelProviders.of(activity, factory).get(factory.getGraphSub().operationId() + "$" + factory.getGraphSub().variables().valueMap().hashCode(), CoreKitSubViewModel.class);
	}

	public static CoreKitSubViewModel getInstance(FragmentActivity activity, DefaultFactory factory) {
		return ViewModelProviders.of(activity, factory).get(factory.getGraphSub().operationId() + "$" + factory.getGraphSub().variables().valueMap().hashCode(), CoreKitSubViewModel.class);
	}

	public static CoreKitSubViewModel getInstance(Fragment fragment, CustomClientFactory factory) {
		return ViewModelProviders.of(fragment, factory).get(factory.getGraphSub().operationId() + "$" + factory.getGraphSub().variables().valueMap().hashCode(), CoreKitSubViewModel.class);
	}

	public static CoreKitSubViewModel getInstance(Fragment fragment, DefaultFactory factory) {
		return ViewModelProviders.of(fragment, factory).get(factory.getGraphSub().operationId() + "$" + factory.getGraphSub().variables().valueMap().hashCode(), CoreKitSubViewModel.class);
	}

	private final ABCoreKitClient mABCoreKitClient;
	private Channel channel;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Class<T> tClass;
	private final String graphQlSubId;
	private CoreKitSubCallBack<T> mCoreKitSubCallBack;
	private boolean isJoin = false;
	private Binding mBinding;
	private boolean isSubed = false;
	private final D mGraphSub;
	private Boolean needOpen = true;
	private boolean isCleared = false;

	public CoreKitSubViewModel(Context context, int apiType, D graphSub, Class<T> tClass) {
		this.mABCoreKitClient = ABCoreKitClient.defaultInstance(context, apiType);
		this.tClass = tClass;
		this.mGraphSub = graphSub;
		this.graphQlSubId = graphSub.operationId() + "$" + graphSub.variables().valueMap().hashCode();
		this.initChannel();
		this.mABCoreKitClient.getCoreKitSocket().onOpen(new InitISocketOpenCallback(this));
		this.mABCoreKitClient.getCoreKitSocket().onClose(new InitISocketCloseCallback(this));
		this.mABCoreKitClient.getCoreKitSocket().onError(new InitIErrorCallback(this));
	}

	public CoreKitSubViewModel(ABCoreKitClient aBCoreKitClient, D graphSub, Class<T> tClass) {
		this.mABCoreKitClient = aBCoreKitClient;
		this.tClass = tClass;
		this.mGraphSub = graphSub;
		this.graphQlSubId = graphSub.operationId() + "$" + graphSub.variables().valueMap().hashCode();
		this.initChannel();
		this.mABCoreKitClient.getCoreKitSocket().onOpen(new InitISocketOpenCallback(this));
		this.mABCoreKitClient.getCoreKitSocket().onClose(new InitISocketCloseCallback(this));
		this.mABCoreKitClient.getCoreKitSocket().onError(new InitIErrorCallback(this));
	}

	/**
	 * init channel
	 */
	private void initChannel() {
		if (channel == null) {
			channel = mABCoreKitClient.getCoreKitSocket().chan(Channel.CORE_KIT_TOPIC, null);
		}
	}

	public void setCoreKitSubCallBack(CoreKitSubCallBack<T> coreKitSubCallBack) {
		mCoreKitSubCallBack = coreKitSubCallBack;
	}

	public void setCoreKitSocketStatusCallBack(CoreKitSocketStatusCallBack callBack) {
		mABCoreKitClient.addSocketStatusCallBack(callBack);
	}

	public void doManualReconnect() {
		mABCoreKitClient.doManualReconnect();
	}

	public CoreKitSubViewModel<T, D> subscription() {
		if (isSubed) {
			CoreKitLogUtils.e("The query Doc have been sub, can not set again");
			return this;
		}
		isSubed = true;
		return doFinalSubscription();
	}

	private CoreKitSubViewModel<T, D> doFinalSubscription() {
		makeFlow()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Subscriber<CoreKitBean<T>>() {
					@Override
					public void onSubscribe(Subscription s) {
						s.request(128);
					}

					@Override
					public void onNext(CoreKitBean<T> coreKitBean) {
						if (mCoreKitSubCallBack != null && coreKitBean.getData() != null) {
							mCoreKitSubCallBack.onNewData(coreKitBean);
						}
					}


					@Override
					public void onError(Throwable t) {
						if (mCoreKitSubCallBack != null) {
							mCoreKitSubCallBack.onNewData(new CoreKitBean(null, CoreKitBean.FAIL_CODE, t.toString()));
						}
					}

					@Override
					public void onComplete() {

					}
				});
		return this;
	}

	private Flowable<CoreKitBean<T>> makeFlow() {
		return Flowable.create(new FlowableOnSubscribe<CoreKitBean<T>>() {
			@Override
			public void subscribe(final FlowableEmitter<CoreKitBean<T>> emitter) {
				initChannel(emitter);
			}
		}, BackpressureStrategy.LATEST);
	}

	private void initChannel(final FlowableEmitter<CoreKitBean<T>> emitter) {
		try {
			CoreKitLogUtils.e("*****initChannel********" + channel.toString());
			if (channel.getState() == ChannelState.CLOSED) {
				// only join when the channel is closed
				channel.join().receive("ok", new JoinIMessageCallback(this, emitter));
			} else {
				// if already join, just push a new doc with different payload
				pushDoc(emitter);
			}
		} catch (Exception e) {
			CoreKitLogUtils.e("initChannel=>" + e.toString());
			if (!emitter.isCancelled()) {
				emitter.onError(e);
			}
		}
	}

	private void pushDoc(final FlowableEmitter<CoreKitBean<T>> emitter) {
		if (channel.isNeedPushDoc(graphQlSubId)) {
			CoreKitLogUtils.e("****need push doc*******");
			try {
				OperationClientMessage message = new OperationClientMessage.Start("empty", mGraphSub, mABCoreKitClient.getScalarTypeAdapters());
				JSONObject rootJson = new JSONObject(message.toJsonString());
				JSONObject payLoadJson = new JSONObject(rootJson.getString("payload"));
				String queryDocument = payLoadJson.getString("query");
				String variables = payLoadJson.getString("variables");

				ObjectNode payload = objectMapper.createObjectNode();
				payload.put("query", queryDocument);
				if (!TextUtils.equals(variables.trim(), "{}")) {
					payload.put("variables", variables);
				}
				channel.push("doc", payload).receive("ok", new DocIMessageCallback(this, emitter));
			} catch (Exception e) {
				CoreKitLogUtils.e("pushDoc=>" + e.toString());
				if (!emitter.isCancelled()) {
					emitter.onError(e);
				}
			}
		} else {
			CoreKitLogUtils.e("*****this graphqlId doc already push*****");
			setCoreKitEvent(emitter);
		}
	}

	private void setCoreKitEvent(final FlowableEmitter<CoreKitBean<T>> emitter) {
		CoreKitLogUtils.e("********setCoreKitEvent******");
		if (mBinding == null) {
			mBinding = new Binding(Channel.CORE_KIT_EVENT, channel.getGraphSubAndSubIdMapItemValueByKey(graphQlSubId),
					new EventIMessageCallback(this, emitter, tClass));
		} else {
			mBinding.setCallback(new EventIMessageCallback(this, emitter, tClass));
		}
		channel.on(mBinding);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		CoreKitLogUtils.e("******onCleared*******");
		leaveChannel();
		channel.offByBind(mBinding);
		mBinding = null;
		channel = null;
		mCoreKitSubCallBack = null;
		isCleared = true;
	}

	public void leaveChannel() {
		if (channel != null) {
			try {
				channel.leave(graphQlSubId, channel.getGraphSubAndSubIdMapItemValueByKey(graphQlSubId));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static class CustomClientFactory<T, D extends com.apollographql.apollo.api.Subscription> extends ViewModelProvider.NewInstanceFactory {

		private ABCoreKitClient mABCoreKitClient;
		private Class<T> tClass;
		private D graphSub;


		public CustomClientFactory(ABCoreKitClient aBCoreKitClient, D graphSub, Class<T> tClass) {
			this.mABCoreKitClient = aBCoreKitClient;
			this.tClass = tClass;
			this.graphSub = graphSub;
		}

		public D getGraphSub() {
			return graphSub;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			return (T) new CoreKitSubViewModel(mABCoreKitClient, graphSub, tClass);
		}
	}

	public static class DefaultFactory<T, D extends com.apollographql.apollo.api.Subscription> extends ViewModelProvider.NewInstanceFactory {

		private Context mContext;
		private int apiType;
		private Class<T> tClass;
		private D graphSub;

		public DefaultFactory(Context context, int apiType, D graphSub, Class<T> tClass) {
			this.mContext = context;
			this.apiType = apiType;
			this.tClass = tClass;
			this.graphSub = graphSub;
		}

		public D getGraphSub() {
			return graphSub;
		}

		@NonNull
		@Override
		public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
			return (T) new CoreKitSubViewModel(mContext, apiType, graphSub, tClass);
		}
	}

	public interface CoreKitSubCallBack<T> {
		void onNewData(CoreKitBean<T> coreKitBean);
	}

	private static class InitISocketOpenCallback implements ISocketOpenCallback {

		private WeakReference<CoreKitSubViewModel> ref;

		public InitISocketOpenCallback(CoreKitSubViewModel coreKitSubViewModel) {
			if (coreKitSubViewModel != null) {
				ref = new WeakReference<>(coreKitSubViewModel);
			}
		}

		@Override
		public void onOpen() {
			if (ref == null) {
				return;
			}
			CoreKitSubViewModel v = ref.get();
			if (v == null || v.isCleared) {
				return;
			}
			CoreKitLogUtils.e("CoreKitSubViewModel********onOpen");
			v.needOpen = false;
			v.initChannel();
			v.channel.initStatus();
			v.isJoin = false;
			v.isSubed = false;
			v.doFinalSubscription();
		}
	}

	private static class InitISocketCloseCallback implements ISocketCloseCallback {

		private WeakReference<CoreKitSubViewModel> ref;

		public InitISocketCloseCallback(CoreKitSubViewModel coreKitSubViewModel) {
			if (coreKitSubViewModel != null) {
				ref = new WeakReference<>(coreKitSubViewModel);
			}
		}

		@Override
		public void onClose() {
			if (ref == null) {
				return;
			}
			CoreKitSubViewModel v = ref.get();
			if (v == null) {
				return;
			}
			synchronized (v.needOpen) {
				v.needOpen = true;
			}
		}
	}


	private static class InitIErrorCallback implements IErrorCallback {

		private WeakReference<CoreKitSubViewModel> ref;

		public InitIErrorCallback(CoreKitSubViewModel coreKitSubViewModel) {
			if (coreKitSubViewModel != null) {
				ref = new WeakReference<>(coreKitSubViewModel);
			}
		}

		@Override
		public void onError(final String reason) {
			if (ref == null) {
				return;
			}
			CoreKitSubViewModel v = ref.get();
			if (v == null) {
				return;
			}
			synchronized (v.needOpen) {
				v.needOpen = true;
			}
		}
	}

	private static class JoinIMessageCallback<T> implements IMessageCallback {

		private WeakReference<CoreKitSubViewModel> ref;
		private WeakReference<FlowableEmitter<CoreKitBean<T>>> refEmiiter;

		public JoinIMessageCallback(CoreKitSubViewModel coreKitSubViewModel, FlowableEmitter<CoreKitBean<T>> emitter) {
			if (coreKitSubViewModel != null) {
				ref = new WeakReference<>(coreKitSubViewModel);
			}
			if (emitter != null) {
				refEmiiter = new WeakReference<>(emitter);
			}
		}

		@Override
		public void onMessage(CoreKitMsgBean msgBean) {
			if (ref == null) {
				return;
			}
			CoreKitSubViewModel v = ref.get();
			if (v == null) {
				return;
			}

			if (refEmiiter == null) {
				return;
			}
			FlowableEmitter<CoreKitBean<T>> emitter = refEmiiter.get();
			if (emitter == null) {
				return;
			}

			CoreKitLogUtils.e("join=>onMessage=>" + msgBean);
			if (!v.isJoin) {
				v.pushDoc(emitter);
				v.isJoin = true;
			}
		}
	}

	private static class DocIMessageCallback<T> implements IMessageCallback {

		private WeakReference<CoreKitSubViewModel> ref;
		private WeakReference<FlowableEmitter<CoreKitBean<T>>> refEmiiter;

		public DocIMessageCallback(CoreKitSubViewModel coreKitSubViewModel, FlowableEmitter<CoreKitBean<T>> emitter) {
			if (coreKitSubViewModel != null) {
				ref = new WeakReference<>(coreKitSubViewModel);
			}
			if (emitter != null) {
				refEmiiter = new WeakReference<>(emitter);
			}
		}

		@Override
		public void onMessage(CoreKitMsgBean msgBean) {
			if (ref == null) {
				return;
			}
			CoreKitSubViewModel v = ref.get();
			if (v == null) {
				return;
			}

			if (refEmiiter == null) {
				return;
			}
			FlowableEmitter<CoreKitBean<T>> emitter = refEmiiter.get();
			if (emitter == null) {
				return;
			}

			CoreKitLogUtils.e("doc=>onMessage=>" + msgBean);
			// update subscriptionId for channel
			v.channel.setGraphSubAndSubIdMapItem(v.graphQlSubId, msgBean.getPayload().get("response").get("subscriptionId").asText());
			v.setCoreKitEvent(emitter);
		}
	}

	private static class EventIMessageCallback<T> implements IMessageCallback {

		private WeakReference<CoreKitSubViewModel> ref;
		private WeakReference<FlowableEmitter<CoreKitBean<T>>> refEmiiter;
		private WeakReference<Class<T>> refTClass;

		public EventIMessageCallback(CoreKitSubViewModel coreKitSubViewModel, FlowableEmitter<CoreKitBean<T>> emitter, Class<T> tClass) {
			if (coreKitSubViewModel != null) {
				ref = new WeakReference<>(coreKitSubViewModel);
			}
			if (emitter != null) {
				refEmiiter = new WeakReference<>(emitter);
			}
			if (tClass != null) {
				refTClass = new WeakReference<>(tClass);
			}
		}

		@Override
		public void onMessage(CoreKitMsgBean msgBean) {
			if (ref == null) {
				return;
			}
			CoreKitSubViewModel v = ref.get();
			if (v == null) {
				return;
			}

			if (refEmiiter == null) {
				return;
			}
			FlowableEmitter<CoreKitBean<T>> emitter = refEmiiter.get();
			if (emitter == null) {
				return;
			}

			if (refTClass == null) {
				return;
			}
			Class<T> tClass = refTClass.get();
			if (tClass == null) {
				return;
			}

			CoreKitLogUtils.e("channel EVENT onMessage thread name =>" + Thread.currentThread().getName());
			try {
				if (!TextUtils.isEmpty(msgBean.getTopic()) && msgBean.getTopic().startsWith("__absinthe__:doc:")) {
					String data = msgBean.getPayload().get("result").get("data").toString();
					String tempSubId = msgBean.getPayload().get("subscriptionId").asText("");
					if (TextUtils.equals(tempSubId, v.channel.getGraphSubAndSubIdMapItemValueByKey(v.graphQlSubId))) {
						T temp = new Gson().fromJson(data, tClass);
						if (!emitter.isCancelled()) {
							emitter.onNext(new CoreKitBean(temp, CoreKitBean.SUCCESS_CODE, ""));
						} else {
							v.subscription();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				if (!emitter.isCancelled()) {
					emitter.onError(e);
				}
			}
		}
	}

}
