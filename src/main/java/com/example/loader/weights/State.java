package com.example.loader.weights;

import com.example.core.model.tensor.ArrayFloatTensor;
import com.example.core.model.tensor.FloatTensor;
import com.example.model.Configuration;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.util.stream.Stream;

public final class State {

    // current wave of activations
    public final FloatTensor x; // activation at current time stamp (dim,)
    public final FloatTensor xb; // same, but inside a residual branch (dim,)
    public final FloatTensor xb2; // an additional buffer just for convenience (dim,)
    public final FloatTensor hb; // buffer for hidden dimension in the ffn (hidden_dim,)
    public final FloatTensor hb2; // buffer for hidden dimension in the ffn (hidden_dim,)
    public final FloatTensor q; // query (dim,)
    public final FloatTensor k; // key (dim,)
    public final FloatTensor v; // value (dim,)
    public final FloatTensor att; // buffer for scores/attention values (n_heads, seq_len)
    public final FloatTensor logits; // output logits
    public final int batchsize;

    // kv cache
    public final FloatTensor[] keyCache;   // (n_layer, seq_len, kv_dim)
    public final FloatTensor[] valueCache; // (n_layer, seq_len, kv_dim)

    // Wrappers for TornadoVM compatibility (FloatArray data structure for TornadoVM acceleration)
    // TornadoVM uses FloatArray for more efficient handling of data, particularly when running on GPU or other accelerators.
    public final FloatArray wrapLogits; // FloatArray wrapper for the logits tensor, compatible with TornadoVM for GPU execution.
    public final FloatArray wrapXb;     // FloatArray wrapper for xb (residual branch activation), optimized for TornadoVM usage.
    public final FloatArray wrapXb2;    // FloatArray wrapper for xb2, another residual buffer to aid in computations with TornadoVM.
    public final FloatArray wrapHb;     // FloatArray wrapper for hb (hidden dimension buffer for FFN), optimized for TornadoVM.
    public final FloatArray wrapHb2;    // FloatArray wrapper for hb2, additional hidden buffer for FFN, for compatibility with TornadoVM.
    public final FloatArray wrapX;     // FloatArray wrapper for the current activation tensor, optimized for TornadoVM.

    public final FloatArray wrapQ; // FloatArray wrapper for the query tensor, optimized for TornadoVM.
    public final FloatArray wrapK; // FloatArray wrapper for the key tensor, optimized for TornadoVM.
    public final FloatArray wrapV; // FloatArray wrapper for the value tensor, optimized for TornadoVM.
    public final FloatArray wrapAtt; // FloatArray wrapper for the attention scores, optimized for TornadoVM.
    public final FloatArray wrapKeyCache;// FloatArray wrapper for the key cache, optimized for TornadoVM.
    public final FloatArray wrapValueCache; // FloatArray wrapper for the value cache, optimized for TornadoVM.
    public final IntArray positionHolder;

    // store inter
    //
    public int localSize;
    public FloatArray temp; // Temporary buffer for intermediate calculations, size adjusted for local workgroup size.
    public FloatArray tempFFN; // Temporary buffer for feed-forward network calculations, size adjusted for local workgroup size.
    public FloatArray tempLogits; // Temporary buffer for logits calculations, size adjusted for local workgroup size.

    public int latestToken;             // Keeps track of the most recent token processed by the model. Useful for stateful or autoregressive models.

    /** last index in previous block */

    public State(Configuration config, int batchsize) {
        this.batchsize = -1;

        this.x = ArrayFloatTensor.allocate(config.dim());
        this.xb = ArrayFloatTensor.allocate(config.dim());
        this.xb2 = ArrayFloatTensor.allocate(config.dim());
        this.hb = ArrayFloatTensor.allocate(config.hiddenDim());
        this.hb2 = ArrayFloatTensor.allocate(config.hiddenDim());
        this.q = ArrayFloatTensor.allocate(config.dim());
        this.k = ArrayFloatTensor.allocate(config.dim());
        this.v = ArrayFloatTensor.allocate(config.dim());
        this.att = ArrayFloatTensor.allocate(config.numberOfHeads(), config.contextLength());
        this.logits = ArrayFloatTensor.allocate(config.vocabularySize());
        int kvDim = (config.dim() * config.numberOfKeyValueHeads()) / config.numberOfHeads();
        this.keyCache = Stream.generate(() -> ArrayFloatTensor.allocate(config.contextLength(), kvDim)).limit(config.numberOfLayers()).toArray(FloatTensor[]::new);
        this.valueCache = Stream.generate(() -> ArrayFloatTensor.allocate(config.contextLength(), kvDim)).limit(config.numberOfLayers()).toArray(FloatTensor[]::new);

        this.wrapX = new FloatArray(config.dim());
        this.wrapXb = new FloatArray(config.dim());
        this.wrapXb2 = new FloatArray(config.dim());
        this.wrapHb = new FloatArray(config.hiddenDim());
        this.wrapHb2 = new FloatArray(config.hiddenDim());

        this.wrapLogits = new FloatArray(config.vocabularySize());
        this.wrapQ = new FloatArray(config.dim());
        this.wrapK = new FloatArray(config.dim());
        this.wrapV = new FloatArray(config.dim());

        // dim vs kvdim
        this.wrapKeyCache = new FloatArray(config.contextLength() * kvDim * config.numberOfLayers());
        this.wrapValueCache = new FloatArray(config.contextLength() * kvDim * config.numberOfLayers());
        this.wrapValueCache.init(0.f);
        this.wrapKeyCache.init(0.f);
        this.wrapAtt = new FloatArray(config.numberOfHeads() * config.contextLength());
        this.positionHolder = new IntArray(1);
        this.latestToken = -1;

        //
        this.localSize = 256;
        // You need at least 9 elements: 1 for the final result + 8 for the workgroup partial sums
        this.temp = new FloatArray(1 + ((config.dim() + localSize-1) / localSize));
        this.tempFFN = new FloatArray(1 + ((config.dim() + localSize-1) / localSize));
        this.tempLogits = new FloatArray(1 + ((config.dim() + localSize-1) / localSize));
    }

    @Override
    public State clone() throws CloneNotSupportedException {
        return (State) super.clone();
    }
}