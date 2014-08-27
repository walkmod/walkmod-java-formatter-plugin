package org.walkmod.javaformatter.writers;

import java.io.File;

import org.apache.ivy.util.FileUtil;
import org.junit.Assert;
import org.junit.Test;
import org.walkmod.walkers.AbstractWalker;
import org.walkmod.walkers.VisitorContext;

public class EclipseWriterTest {

	@Test
	public void testSpacesInsteadOfTabs() throws Exception {
		EclipseWriter ew = new EclipseWriter();
		VisitorContext ctx = new VisitorContext();
		File outputDir = new File("src/test/output");
		outputDir.mkdirs();
		File output = new File(outputDir, "Foo.java");

		ctx.put(AbstractWalker.ORIGINAL_FILE_KEY, output);

		ew.write("\t public class Foo { }", ctx);

		ew.close();

		String code = FileUtil.readEntirely(output);

		output.delete();
		outputDir.delete();
		Assert.assertFalse(code.charAt(0) == '\t');

	}
}
