package org.bytefred.arrview.ui

actual val isWeb: Boolean = false

actual val ctrlClickIsRightClick: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")
