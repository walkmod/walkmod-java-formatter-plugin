/* 
  Copyright (C) 2013 Raquel Pau and Albert Coroleu.
 
 Walkmod is free software: you can redistribute it and/or modify
 it under the terms of the GNU Lesser General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 
 Walkmod is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public License
 along with Walkmod.  If not, see <http://www.gnu.org/licenses/>.*/
package org.walkmod.javaformatter.writers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.walkmod.ChainWriter;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.body.ModifierSet;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.util.FileUtils;
import org.walkmod.util.DomHelper;
import org.walkmod.walkers.VisitorContext;
import org.walkmod.writers.AbstractFileWriter;
import org.xml.sax.InputSource;

public class EclipseWriter extends AbstractFileWriter implements ChainWriter {

	private String compilerSource = JavaCore.VERSION_1_7;

	private String compilerCompliance = JavaCore.VERSION_1_7;

	private String compilerTargetPlatform = JavaCore.VERSION_1_7;

	private Boolean overrideConfigCompilerVersion = false;

	private String configFile = "formatter.xml";

	public static final String CODE_FORMATTER_PROFILE = "CodeFormatterProfile";

	private CodeFormatter formatter = null;

	private static Logger log = Logger.getLogger(EclipseWriter.class);

	private String formatFile(String code) {
		TextEdit te = null;
		if (formatter == null) {
			log.debug("starting Eclipse formatter");
			Map<String, String> options = getFormattingOptions();
			formatter = ToolFactory.createCodeFormatter(options);
			if (formatter != null) {
				log.debug("Eclipse formatter [ok]");
			}
		}
		if (formatter != null) {
			te = formatter.format(CodeFormatter.K_COMPILATION_UNIT, code, 0,
					code.length(), 0, String.valueOf('\n'));
			if (te == null) {
				log.warn("The source cannot be formatted with the selected configuration. Applying a default formatting");
				return code;

			}
			IDocument doc = new org.eclipse.jface.text.Document(code);
			try {
				te.apply(doc);
			} catch (Exception e) {
				throw new WalkModException(e);
			}
			String formattedCode = doc.get();
			if (formattedCode == null || "".equals(formattedCode)) {
				return code;
			}
			return formattedCode;
		} else {
			throw new WalkModException(
					"Eclipse formatter cannot be initialized");
		}
	}

	/**
	 * Return the options to be passed when creating {@link CodeFormatter}
	 * instance.
	 * 
	 * @return
	 * @throws MojoExecutionException
	 */
	private Map<String, String> getFormattingOptions() {
		Map<String, String> options = new HashMap<String, String>();
		options.put(JavaCore.COMPILER_SOURCE, compilerSource);
		options.put(JavaCore.COMPILER_COMPLIANCE, compilerCompliance);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
				compilerTargetPlatform);
		if (configFile != null) {
			log.debug("loading Eclipse formatting rules from " + configFile);
			Map<String, String> config = getOptionsFromConfigFile();
			if (Boolean.TRUE.equals(overrideConfigCompilerVersion)) {
				config.remove(JavaCore.COMPILER_SOURCE);
				config.remove(JavaCore.COMPILER_COMPLIANCE);
				config.remove(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM);
			}
			options.putAll(config);
		} else {
			log.warn("eclipse formatting rules are unknown");
		}
		return options;
	}

	/**
	 * Read config file and return the config as {@link Map}.
	 * 
	 * @return
	 * @throws MojoExecutionException
	 */
	private Map<String, String> getOptionsFromConfigFile() {
		InputStream configInput = null;
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		File file = new File(configFile);
		try {
			if (!file.exists()) {
				configInput = loader.getResourceAsStream(this.configFile);
				if (configInput == null) {
					throw new WalkModException("Config file [" + configFile
							+ "] cannot be found");
				}
				log.debug("The formatter file "
						+ file.getAbsolutePath()
						+ " [ not found]. Loading it from the $WALKMOD_HOME/conf dir");
			} else {
				log.debug("The formatter file " + file.getAbsolutePath()
						+ " [found]");
				configInput = new FileInputStream(file);
			}
			InputSource in = new InputSource(configInput);
			in.setSystemId(configFile);
			Document doc = DomHelper.parse(in);
			Element profilesElem = doc.getDocumentElement();
			Map<String, String> options = new HashMap<String, String>();
			if ("profiles".equals(profilesElem.getNodeName())) {
				NodeList children = profilesElem.getChildNodes();
				boolean loadProfile = false;
				int childSize = children.getLength();
				for (int i = 0; i < childSize && !loadProfile; i++) {
					Node childNode = children.item(i);
					if (childNode instanceof Element) {
						Element child = (Element) childNode;
						if ("profile".equals(child.getNodeName())) {
							if (CODE_FORMATTER_PROFILE.equals(child
									.getAttribute("kind"))) {
								NodeList settings = child.getChildNodes();
								int settingsSize = settings.getLength();
								for (int j = 0; j < settingsSize; j++) {
									Node settingNode = settings.item(j);
									if (settingNode instanceof Element) {
										Element setting = (Element) settingNode;
										options.put(setting.getAttribute("id"),
												setting.getAttribute("value"));

									}
								}
								loadProfile = true;
							}
						}
					}
				}
			}
			return options;
		} catch (Exception e) {
			throw new WalkModException("Cannot read config file [" + configFile
					+ " ]");
		} finally {
			if (configInput != null) {
				try {
					configInput.close();
				} catch (IOException e) {
					throw new WalkModException(e);
				}
			}
		}
	}

	@Override
	public String getContent(Object n, VisitorContext vc) {

		File file = (File) vc.get("outFile");
		if (file != null && file.exists()) {
			
			try {
				// to avoid losing some information when the AST is
				// rewritten.
				Boolean isUpdated = (Boolean) vc.get("isUpdated");
				if (isUpdated != null && isUpdated.equals(Boolean.FALSE)) {
					
					return formatFile(org.apache.commons.io.FileUtils
							.readFileToString(file));
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		

		return formatFile(n.toString());
	}

	@Override
	public File createOutputDirectory(Object o) {
		File out = null;
		if (o instanceof CompilationUnit) {
			CompilationUnit n = (CompilationUnit) o;
			List<TypeDeclaration> types = n.getTypes();
			if (types != null) {
				Iterator<TypeDeclaration> it = types.iterator();
				boolean found = false;
				while (it.hasNext() && !found) {
					TypeDeclaration td = it.next();
					if (ModifierSet.isPublic(td.getModifiers())) {
						found = true;
						out = FileUtils.getSourceFile(getOutputDirectory(),
								n.getPackage(), td);
						if (!out.exists()) {
							try {
								FileUtils.createSourceFile(
										getOutputDirectory(), out);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						}
					}
				}
			}
		}
		return out;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}
}
