package org.bytefred.arrview.ui

/** True in the browser build: the header then shows the web-only subtitle. */
expect val isWeb: Boolean

/** Ctrl+click opens the context menu like a right-click (macOS). */
expect val ctrlClickIsRightClick: Boolean
