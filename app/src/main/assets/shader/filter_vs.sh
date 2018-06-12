/**
* highp for vertex positions,
* mediump for texture coordinates,
* lowp for colors.
*/

precision mediump float;

attribute vec2 aPosition;
attribute vec4 vPosition;

uniform lowp vec2 uScaleFactFilter;

varying vec2 vTextureCoord;
varying vec2 vTextureCoordFilter;

void main() {
	gl_Position = vPosition;//vec4(aPosition, 0.0, 1.0);›

	lowp vec2 position = vec2(1.0, 1.0);

	vTextureCoord = 0.5 * (position * aPosition) + 0.5;
	vTextureCoordFilter = 0.5 * (uScaleFactFilter * aPosition) + 0.5;
	vTextureCoordFilter.y = 1.0 - vTextureCoordFilter.y;
}