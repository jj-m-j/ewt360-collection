package eightbitlab.com.blurview.internal;

/**
 * Linear-sample Gaussian taps for one separable blur axis, computed on the CPU. The kernel is
 * symmetric, so only the right half is stored and each stored tap serves the +/- pair; the t=0 tap is
 * implicit with unit weight. Recomputed only when the radius changes (cheap, even when animated).
 */
final class GaussianKernel {

    static final int MAX_SAMPLES = 24;

    private final float[] offsets = new float[MAX_SAMPLES];
    private final float[] weights = new float[MAX_SAMPLES];
    private int sampleCount;
    private float radius = Float.NaN;

    void ensureRadius(float blurRadius) {
        if (blurRadius == radius) {
            return;
        }
        radius = blurRadius;
        float sigma = blurRadius * 0.57735f + 0.5f;
        float twoSigmaSquared = 2f * sigma * sigma;
        int radiusTexels = Math.max(1, (int) Math.ceil(3f * sigma));
        int count = 0;
        int texel = 1;
        while (texel <= radiusTexels && count < MAX_SAMPLES) {
            float firstWeight = (float) Math.exp(-(double) (texel * texel) / twoSigmaSquared);
            if (texel + 1 <= radiusTexels) {
                float secondWeight = (float) Math.exp(-(double) ((texel + 1) * (texel + 1)) / twoSigmaSquared);
                float pairWeight = firstWeight + secondWeight;
                offsets[count] = (texel * firstWeight + (texel + 1) * secondWeight) / pairWeight;
                weights[count] = pairWeight;
                texel += 2;
            } else {
                offsets[count] = texel;
                weights[count] = firstWeight;
                texel += 1;
            }
            count++;
        }
        sampleCount = count;
    }

    int sampleCount() {
        return sampleCount;
    }

    float[] offsets() {
        return offsets;
    }

    float[] weights() {
        return weights;
    }
}
