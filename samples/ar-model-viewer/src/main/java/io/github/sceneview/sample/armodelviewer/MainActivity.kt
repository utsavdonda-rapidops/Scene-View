package io.github.sceneview.sample.armodelviewer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.sample.doOnApplyWindowInsets
import io.github.sceneview.sample.setFullScreen
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView

    var x = 0.0f
    var y = 0.0f
    var z = 0.0f
    var w = 0.0f
    var defaultMaterialInstances: List<MaterialInstance>? = null


    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    var anchorNodeView: View? = null

    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    fun updateInstructions() {
        instructionText.text = trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.point_your_phone_down)
        } else {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val buttonGreen: Button = findViewById(R.id.b1)
        val buttonRed: Button = findViewById(R.id.b2)
        val buttonDefault: Button = findViewById(R.id.b3)
        val buttonReset: Button = findViewById(R.id.b4)

        buttonGreen.setOnClickListener {
            x = 0.0f
            y = 1.0f
            z = 0.0f
            w = 1.0f
            // Reload model with new color
            anchorNode?.let {
                reloadModel(it)
            }
            Toast.makeText(this, "Change Color Green", Toast.LENGTH_SHORT).show()
        }

        buttonRed.setOnClickListener {
            // Update color values
            x = 1.0f
            y = 0.0f
            z = 0.0f
            w = 1.0f
            Toast.makeText(this, "Change Color Red", Toast.LENGTH_SHORT).show()

            // Reload model with new color
            anchorNode?.let {
                reloadModel(it)
            }
        }

        buttonDefault.setOnClickListener {
            // Reset color values
            x = 0.0f
            y = 0.0f
            z = 0.0f
            w = 0.0f
            Toast.makeText(this, "Reset Color", Toast.LENGTH_SHORT).show()

            // Reload model with default color
            anchorNode?.let {
                reloadModel(it)
            }
        }

        buttonReset.setOnClickListener {
            resetAR()
            Toast.makeText(this, "Reset and start new plane detection", Toast.LENGTH_SHORT).show()
        }

        setFullScreen(
            findViewById(R.id.rootView),
            fullScreen = true,
            hideSystemBars = false,
            fitsSystemWindows = false
        )

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar)?.apply {
            doOnApplyWindowInsets { systemBarsInsets ->
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
            }
            title = ""
        })
        instructionText = findViewById(R.id.instructionText)
        loadingView = findViewById(R.id.loadingView)
        sceneView = findViewById<ARSceneView?>(R.id.sceneView).apply {
            lifecycle = this@MainActivity.lifecycle
            planeRenderer.isEnabled = true
            configureSession { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            }
            onSessionUpdated = { _, frame ->
                if (anchorNode == null) {
                    frame.getUpdatedPlanes()
                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                        ?.let { plane ->
                            addAnchorNode(plane.createAnchor(plane.centerPose))
                        }
                }
            }
            onTrackingFailureChanged = { reason ->
                this@MainActivity.trackingFailureReason = reason
            }
        }
    }

    fun addAnchorNode(anchor: Anchor) {
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor).apply {
                isEditable = true
                lifecycleScope.launch {
                    isLoading = true
                    buildModelNode()?.let {
                        addChildNode(it)
                    }
                    isLoading = false
                }
                anchorNode = this
            }
        )
    }

    suspend fun buildModelNode(): ModelNode? {
        return sceneView.modelLoader.loadModelInstance(
            "https://sceneview.github.io/assets/models/DamagedHelmet.glb"
        )?.let { modelInstance ->
            modelInstance.materialInstances.forEachIndexed { index, materialInstance ->
                Log.d("YourTag", "Index: $index")
                when {
                    x == 0.0f && y == 0.0f && z == 0.0f && w == 0.0f -> {
                        defaultMaterialInstances = listOf(materialInstance)
                        x=1.0f
                        Log.d("YourTag", "Color: ${defaultMaterialInstances}")
                    }

                    else -> {
                        defaultMaterialInstances = listOf(materialInstance)
                        materialInstance.setParameter("baseColorFactor", x, y, z, w)
                        Log.d("YourTag", "Color: ${defaultMaterialInstances}")

                    }

                }
            }

            return ModelNode(
                modelInstance = modelInstance,
                // Scale to fit in a 0.5 meters cube
                scaleToUnits = 0.5f,
                // Bottom origin instead of center so the model base is on floor
                centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = true
            }
        }
        return null
    }

    fun reloadModel(node: AnchorNode) {
        lifecycleScope.launch {
            isLoading = true
            val newModelNode = buildModelNode()
            if (newModelNode != null) {
                node.clearChildNodes() // Remove existing child nodes
                node.addChildNode(newModelNode)
            }
            isLoading = false
        }
    }

    fun AnchorNode.clearChildNodes() {
        val nodes = ArrayList(childNodes)
        nodes.forEach {
            removeChildNode(it)
        }
    }

    private fun resetAR() {

        val arSession: Session? = sceneView.session
        val currentFrame = arSession?.update()
        val cameraPose = currentFrame?.camera?.pose
// Calculate a new position based on the camera position and orientation
        val newPosition = cameraPose?.compose(Pose.makeTranslation(0f, 0f, -1f))
// Update the anchor node position based on the new calculated position
        newPosition?.let {
            val newAnchor = sceneView.session?.createAnchor(it)
            newAnchor?.let { anchor ->
// Remove the previous anchor node
                anchorNode?.let { previousAnchorNode ->
                    sceneView.removeChildNode(previousAnchorNode)
                }
// Add the new anchor node
                addAnchorNode(anchor)
            }
        }
        Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
// Reload model with default color
        anchorNode?.let {
            reloadModel(it)
        }


        // Remove existing anchor node and its children
//        anchorNode?.clearChildNodes()
//        anchorNode?.let { node ->
//            sceneView.removeChildNode(node)
//            anchorNode = null
//        }
//
//        // Reset the AR session configuration
//
//        // Clear the tracking failure reason
//        trackingFailureReason = null
    }

}
