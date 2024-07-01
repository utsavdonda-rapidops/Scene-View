package io.github.sceneview.sample.armodelviewer

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
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
import io.github.sceneview.material.setBaseColorMap
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.sample.armodelviewer.databinding.RgbLayoutDialogBinding
import io.github.sceneview.sample.doOnApplyWindowInsets
import io.github.sceneview.sample.setFullScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var sceneView: ARSceneView
    private lateinit var loadingView: View
    private lateinit var instructionText: TextView
    private lateinit var resetButton: Button
    private lateinit var colorSpinner: Spinner
    private lateinit var selectColorButton: Button
    private lateinit var btnPickImage: Button
    private var imgUri: Uri? = null
    var currentIndex: Int = -1
    private lateinit var adapter: ArrayAdapter<String>
    private val colorMap: MutableList<MaterialInstance> = mutableListOf()
    private var glbMaterial = mutableListOf("Select Material")
    private var isReset = true
    private var glbUrl =
        "https://firebasestorage.googleapis.com/v0/b/fir-practice-7ec8c.appspot.com/o/Scene.glb?alt=media&token=c4f3f0b0-d457-4a25-9eef-e5a8938d6619"
    private var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }
    private var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }
    private var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    private fun updateInstructions() {
        instructionText.text =
            trackingFailureReason?.getDescription(this) ?: if (anchorNode == null) {
                getString(R.string.point_your_phone_down)
            } else {
                null
            }
    }

    private val rgbLayoutDialogBinding: RgbLayoutDialogBinding by lazy {
        RgbLayoutDialogBinding.inflate(layoutInflater)
    }

    private fun setOnSeekbar(
        type: String, typeTxt: TextView, seekBar: SeekBar, colorTxt: TextView
    ) {
        typeTxt.text = type
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                colorTxt.text = seekBar.progress.toString()
                setRGBColor()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        colorTxt.text = seekBar.progress.toString()
    }

    private fun setRGBColor(): String {
        val red = rgbLayoutDialogBinding.redLayout.seekBar.progress
        val green = rgbLayoutDialogBinding.greenLayout.seekBar.progress
        val blue = rgbLayoutDialogBinding.blueLayout.seekBar.progress
        val hex = String.format(
            "#%02x%02x%02x", red, green, blue
        )
        rgbLayoutDialogBinding.colorView.setBackgroundColor(Color.parseColor(hex))
        return hex
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetButton = findViewById(R.id.button4)
        colorSpinner = findViewById(R.id.colorSpinner)
        selectColorButton = findViewById(R.id.b1)
        btnPickImage = findViewById(R.id.pickImage)
        val rgbDialog = Dialog(this).apply {
            setContentView(rgbLayoutDialogBinding.root)
            window!!.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCancelable(false)
        }
        setOnSeekbar(
            "R",
            rgbLayoutDialogBinding.redLayout.typeTxt,
            rgbLayoutDialogBinding.redLayout.seekBar,
            rgbLayoutDialogBinding.redLayout.colorValueTxt
        )
        setOnSeekbar(
            "G",
            rgbLayoutDialogBinding.greenLayout.typeTxt,
            rgbLayoutDialogBinding.greenLayout.seekBar,
            rgbLayoutDialogBinding.greenLayout.colorValueTxt
        )
        setOnSeekbar(
            "B",
            rgbLayoutDialogBinding.blueLayout.typeTxt,
            rgbLayoutDialogBinding.blueLayout.seekBar,
            rgbLayoutDialogBinding.blueLayout.colorValueTxt
        )
        rgbLayoutDialogBinding.cancelBtn.setOnClickListener {
            rgbDialog.dismiss()
        }
        rgbLayoutDialogBinding.pickBtn.setOnClickListener {
            val red = rgbLayoutDialogBinding.redLayout.seekBar.progress / 255.0f
            val green = rgbLayoutDialogBinding.greenLayout.seekBar.progress / 255.0f
            val blue = rgbLayoutDialogBinding.blueLayout.seekBar.progress / 255.0f
            if (currentIndex in colorMap.indices) {
                val whiteTexture = createTextureFromUriAndColor()
                colorMap[currentIndex] = colorMap[currentIndex].apply {
                    // Set the base color factor to the desired color
                    setParameter("baseColorFactor", red, green, blue, 1.0f)
                    if (whiteTexture != null) {
                        setBaseColorMap(whiteTexture)
                    }
                }
                isReset = false
            }
            rgbDialog.dismiss()
        }

        selectColorButton.setOnClickListener {
            rgbDialog.show()
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, glbMaterial)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorSpinner.adapter = adapter
        colorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View, position: Int, id: Long
            ) {
                currentIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        btnPickImage.setOnClickListener {
            val pickImg = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            changeImage.launch(pickImg)
        }
        resetButton.setOnClickListener {
            glbMaterial.clear()
            colorMap.clear()
            resetButton.visibility = View.INVISIBLE
            selectColorButton.visibility = View.INVISIBLE
            colorSpinner.visibility = View.INVISIBLE
            btnPickImage.visibility = View.INVISIBLE
            isReset = true
            val arSession: Session? = sceneView.session
            val currentFrame = arSession?.update()
            val cameraPose = currentFrame?.camera?.pose
            val newPosition = cameraPose?.compose(Pose.makeTranslation(0f, 0f, -1f))
            newPosition?.let {
                val newAnchor = sceneView.session?.createAnchor(it)
                newAnchor?.let { anchor ->
                    anchorNode?.let { previousAnchorNode ->
                        sceneView.removeChildNode(previousAnchorNode)
                    }
                    addAnchorNode(anchor)
                }
            }
            Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
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
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            }
            onSessionUpdated = { _, frame ->
                if (anchorNode == null) {
                    frame.getUpdatedPlanes()
                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING || it.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING || it.type == Plane.Type.VERTICAL }
                        ?.let { plane ->
                            addAnchorNode(plane.createAnchor(plane.centerPose))
                            resetButton.visibility = View.VISIBLE
                            selectColorButton.visibility = View.VISIBLE
                            colorSpinner.visibility = View.VISIBLE
                            btnPickImage.visibility = View.VISIBLE
                        }
                }
            }
            onTrackingFailureChanged = { reason ->
                this@MainActivity.trackingFailureReason = reason
            }
        }
    }

    private fun addAnchorNode(anchor: Anchor) {
        sceneView.addChildNode(AnchorNode(sceneView.engine, anchor).apply {
            isEditable = true
            sceneView.view.blendMode.name
            lifecycleScope.launch {
                isLoading = true
                buildModelNode()?.let { modelNode ->
                    addChildNode(modelNode)
                }
                resetButton.visibility = View.VISIBLE
                selectColorButton.visibility = View.VISIBLE
                colorSpinner.visibility = View.VISIBLE
                btnPickImage.visibility = View.VISIBLE
                isLoading = false
            }
            anchorNode = this
        })
    }

    private suspend fun buildModelNode(): ModelNode? {
        glbMaterial.clear()
        return sceneView.modelLoader.loadModelInstance(
            glbUrl
        )?.let { modelInstance ->
            modelInstance.materialInstances.forEachIndexed { index, materialInstance ->
                glbMaterial.add(materialInstance.name)
                if (index < colorMap.size) {
                    colorMap[index] = materialInstance
                } else {
                    colorMap.add(materialInstance)
                }
            }
            adapter.notifyDataSetChanged()

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
    }


    private fun createTextureFromUriAndColor(): Texture? {
        val buffer: Buffer
        val texture: Texture

        return try {
            if (imgUri != null) {
                val inputStream = contentResolver.openInputStream(imgUri!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val width = bitmap.width
                val height = bitmap.height
                buffer =
                    ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
                bitmap.copyPixelsToBuffer(buffer)
                buffer.flip()

                texture = Texture.Builder().width(width).height(height).levels(1)
                    .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8)
                    .build(sceneView.engine)

                val pixelBufferDescriptor = Texture.PixelBufferDescriptor(
                    buffer, Texture.Format.RGBA, Texture.Type.UBYTE
                )
                texture.setImage(sceneView.engine, 0, pixelBufferDescriptor)

                texture
            } else {
                // Handle case where imgUri is null (if needed)
                buffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                buffer.put(0, 255.toByte()) // Red
                buffer.put(1, 255.toByte()) // Green
                buffer.put(2, 255.toByte()) // Blue
                buffer.put(3, 255.toByte()) // Alpha

                texture = Texture.Builder().width(1).height(1).levels(1)
                    .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8)
                    .build(sceneView.engine)

                val pixelBufferDescriptor = Texture.PixelBufferDescriptor(
                    buffer, Texture.Format.RGBA, Texture.Type.UBYTE
                )
                texture.setImage(sceneView.engine, 0, pixelBufferDescriptor)
                texture
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create texture", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private val changeImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val data = it.data
                imgUri = data?.data
                if (imgUri != null) {
                    isLoading = true // Start loading indicator

                    // Perform the image processing asynchronously
                    lifecycleScope.launch {
                        val texture = withContext(Dispatchers.Default) {
                            createTextureFromUriAndColor()
                        }

                        // Apply the texture to the material on the main thread
                        withContext(Dispatchers.Main) {
                            if (texture != null) {
                                applyTextureToMaterial(texture)
                            }
                            isLoading = false // End loading indicator after applying the texture
                        }
                    }
                }
            }
        }

    private fun applyTextureToMaterial(texture: Texture) {
        if (currentIndex in colorMap.indices) {
            colorMap[currentIndex].setBaseColorMap(texture)

            // Reload the AR object to ensure the new texture is applied
            anchorNode?.let { node ->
                reloadModel(node)
            }
        } else {
            Log.e("LogDB", "Invalid currentIndex: $currentIndex")
        }
    }

    private fun reloadModel(node: AnchorNode) {
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
}