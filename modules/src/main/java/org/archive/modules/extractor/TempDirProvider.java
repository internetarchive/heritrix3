package org.archive.modules.extractor;

import java.io.File;
import java.io.Serializable;

public interface TempDirProvider extends Serializable {

    File getScratchDisk();

}
