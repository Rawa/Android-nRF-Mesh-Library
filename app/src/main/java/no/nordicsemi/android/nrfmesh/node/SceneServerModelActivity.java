package no.nordicsemi.android.nrfmesh.node;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.Random;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import no.nordicsemi.android.mesh.ApplicationKey;
import no.nordicsemi.android.mesh.Scene;
import no.nordicsemi.android.mesh.models.SceneServer;
import no.nordicsemi.android.mesh.models.SigModelParser;
import no.nordicsemi.android.mesh.transport.MeshMessage;
import no.nordicsemi.android.mesh.transport.MeshModel;
import no.nordicsemi.android.mesh.transport.SceneGet;
import no.nordicsemi.android.mesh.transport.SceneRecall;
import no.nordicsemi.android.mesh.transport.SceneRegisterStatus;
import no.nordicsemi.android.mesh.transport.SceneStatus;
import no.nordicsemi.android.nrfmesh.R;
import no.nordicsemi.android.nrfmesh.scenes.adapter.StoredScenesAdapter;
import no.nordicsemi.android.nrfmesh.scenes.dialog.BottomSheetSceneRecallDialogFragment;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class SceneServerModelActivity extends ModelConfigurationActivity implements BottomSheetSceneRecallDialogFragment.SceneRecallListener, StoredScenesAdapter.OnItemListener {

    protected StoredScenesAdapter mScenesAdapter;
    private MaterialTextView noCurrentScene;
    private MaterialButton getCurrentScene;

    protected void updateUi(final MeshModel model) {
        updateAppStatusUi(model);
        updatePublicationUi(model);
        updateSubscriptionUi(model);
        updateScenesUi(model);
    }

    protected void updateScenesUi(final MeshModel model) {
        if (model != null) {
            final SceneServer sceneServer = (SceneServer) model;
            if (!sceneServer.getScenesNumbers().isEmpty()) {
                noCurrentScene.setVisibility(INVISIBLE);
            } else {
                noCurrentScene.setVisibility(VISIBLE);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final MeshModel model = mViewModel.getSelectedModel().getValue();
        if (model != null && model.getModelId() == SigModelParser.SCENE_SERVER) {
            mSwipe.setOnRefreshListener(this);
            mContainerPublication.setVisibility(GONE);
            final ConstraintLayout container = findViewById(R.id.node_controls_container);
            final View layoutSceneServer = LayoutInflater.from(this).inflate(R.layout.layout_scene_server, container);
            noCurrentScene = layoutSceneServer.findViewById(R.id.no_current_scene_available);
            getCurrentScene = layoutSceneServer.findViewById(R.id.action_read);
            getCurrentScene.setOnClickListener(v -> sendGetCurrentScene());

            final RecyclerView recyclerView = layoutSceneServer.findViewById(R.id.recycler_view_scenes);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.setAdapter(mScenesAdapter = new StoredScenesAdapter(this, mViewModel.getSelectedElement(), mViewModel.getNetworkLiveData()));
            mScenesAdapter.setOnItemClickListener(this);
            mViewModel.getSelectedModel().observe(this, this::updateUi);
        }
    }

    @Override
    protected void enableClickableViews() {
        super.enableClickableViews();
        if (getCurrentScene != null)
            getCurrentScene.setEnabled(true);
    }

    @Override
    protected void disableClickableViews() {
        super.disableClickableViews();
        if (getCurrentScene != null)
            getCurrentScene.setEnabled(false);
    }

    @Override
    public void onRefresh() {
        super.onRefresh();
        final MeshModel model = mViewModel.getSelectedModel().getValue();
        if (model.getModelId() == SigModelParser.SCENE_SERVER) {
            mViewModel.getMessageQueue().add(new SceneGet(getDefaultApplicationKey()));
        }
    }

    @Override
    protected void updateMeshMessage(final MeshMessage meshMessage) {
        super.updateMeshMessage(meshMessage);
        mSwipe.setOnRefreshListener(this);
        if (meshMessage instanceof SceneStatus) {
            final SceneStatus status = (SceneStatus) meshMessage;
            mViewModel.removeMessage();
            if (status.isSuccessful()) {
                handleStatuses();
                updateScenesUi(mViewModel.getSelectedModel().getValue());
            } else {
                displayStatusDialogFragment(getString(R.string.title_sig_model_subscription_list), status.getStatusMessage(status.getStatus()));
            }
        } else if (meshMessage instanceof SceneRegisterStatus) {
            final SceneRegisterStatus status = (SceneRegisterStatus) meshMessage;
            mViewModel.removeMessage();
            if (status.isSuccessful()) {
                handleStatuses();
                updateScenesUi(mViewModel.getSelectedModel().getValue());
            } else {
                displayStatusDialogFragment(getString(R.string.title_sig_model_subscription_list), status.getStatusMessage(status.getStatus()));
            }
        }
        hideProgressBar();
    }

    protected ApplicationKey getDefaultApplicationKey() {
        final MeshModel meshModel = mViewModel.getSelectedModel().getValue();
        if (meshModel != null && !meshModel.getBoundAppKeyIndexes().isEmpty()) {
            return mViewModel.getNetworkLiveData().getAppKeys().get(meshModel.getBoundAppKeyIndexes().get(0));
        }
        return null;
    }

    private void sendGetCurrentScene() {
        final ApplicationKey key = getDefaultApplicationKey();
        if (key != null) {
            sendMessage(new SceneGet(key));
        }
    }

    @Override
    public void recallScene(@NonNull final Scene scene, final int transitionSteps, final int transitionStepResolution, final int delay) {
        sendSceneRecall(scene, transitionSteps, transitionStepResolution, delay);
    }

    private void sendSceneRecall(@NonNull final Scene scene, final int transitionSteps, final int transitionStepResolution, final int delay) {
        final ApplicationKey key = getDefaultApplicationKey();
        if (key != null) {
            final SceneRecall recall;
            if (transitionSteps == 0 && transitionStepResolution == 0 && delay == 0) {
                recall = new SceneRecall(key, scene.getNumber(), new Random().nextInt());
            } else {
                recall = new SceneRecall(key, transitionSteps, transitionStepResolution, delay, scene.getNumber(), new Random().nextInt());
            }
            sendMessage(recall);
        }
    }

    @Override
    public void onItemClick(final int position, @NonNull final Scene scene) {
        BottomSheetSceneRecallDialogFragment.instantiate(scene).show(getSupportFragmentManager(), null);
    }
}
