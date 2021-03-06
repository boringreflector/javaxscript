/*
 * The MIT License (MIT)
 * Copyright (C) 2015 BoringReflector
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE X CONSORTIUM BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package javax.script;
import java.util.*;
import java.net.URL;
import java.io.*;
import java.security.*;
import sun.misc.Service;
import sun.misc.ServiceConfigurationError;
import sun.reflect.Reflection;
import sun.security.util.SecurityConstants;

/**
 * The <code>ScriptEngineManager</code> implements a discovery and instantiation
 * mechanism for <code>ScriptEngine</code> classes and also maintains a
 * collection of key/value pairs storing state shared by all engines created
 * by the Manager. This class uses the <a href="../../../technotes/guides/jar/jar.html#Service%20Provider">service provider</a> mechanism to enumerate all the 
 * implementations of <code>ScriptEngineFactory</code>. <br><br>
 * The <code>ScriptEngineManager</code> provides a method to return an array of all these factories 
 * as well as utility methods which look up factories on the basis of language name, file extension 
 * and mime type.
 * <p>
 * The <code>Bindings</code> of key/value pairs, referred to as the "Global Scope"  maintained
 * by the manager is available to all instances of <code>ScriptEngine</code> created
 * by the <code>ScriptEngineManager</code>.  The values in the <code>Bindings</code> are
 * generally exposed in all scripts.
 */
public class ScriptEngineManager  {
    private static final boolean DEBUG = false;
    /**
     * If the thread context ClassLoader can be accessed by the caller, 
     * then the effect of calling this constructor is the same as calling 
     * <code>ScriptEngineManager(Thread.currentThread().getContextClassLoader())</code>.
     * Otherwise, the effect is the same as calling <code>ScriptEngineManager(null)</code>.
     *
     * @see java.lang.Thread#getContextClassLoader
     */
    public ScriptEngineManager() {
        ClassLoader ctxtLoader = Thread.currentThread().getContextClassLoader();
        if (canCallerAccessLoader(ctxtLoader)) {
            if (DEBUG) System.out.println("using " + ctxtLoader);
            init(ctxtLoader);
        } else {
            if (DEBUG) System.out.println("using bootstrap loader");
            init(null);
        }
    }
    
    /**
     * This constructor loads the implementations of 
     * <code>ScriptEngineFactory</code> visible to the given 
     * <code>ClassLoader</code> using the <a href="../../../technotes/guides/jar/jar.html#Service%20Provider">service provider</a> mechanism.<br><br>
     * If loader is <code>null</code>, the script engine factories that are 
     * bundled with the platform and that are in the usual extension 
     * directories (installed extensions) are loaded. <br><br>
     *
     * @param loader ClassLoader used to discover script engine factories.
     */
    public ScriptEngineManager(ClassLoader loader) {
        init(loader);
    }
    
    private void init(final ClassLoader loader) {
        globalScope = new SimpleBindings();
        engineSpis = new HashSet<ScriptEngineFactory>();
        nameAssociations = new HashMap<String, ScriptEngineFactory>();
        extensionAssociations = new HashMap<String, ScriptEngineFactory>();
        mimeTypeAssociations = new HashMap<String, ScriptEngineFactory>();
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                initEngines(loader);
                return null;
            }
        });
    }

    private void initEngines(final ClassLoader loader) {
        Iterator itr = null;
        try {
            if (loader != null) {
                itr = Service.providers(ScriptEngineFactory.class, loader);
            } else {
                itr = Service.installedProviders(ScriptEngineFactory.class);
            }
        } catch (ServiceConfigurationError err) {
            System.err.println("Can't find ScriptEngineFactory providers: " +
                          err.getMessage());
            if (DEBUG) {
                err.printStackTrace();
            }
            // do not throw any exception here. user may want to
            // manage his/her own factories using this manager
            // by explicit registratation (by registerXXX) methods.
            return;
        }

        try {
            while (itr.hasNext()) {
                try {
                    ScriptEngineFactory fact = (ScriptEngineFactory) itr.next();
                    engineSpis.add(fact);
                } catch (ServiceConfigurationError err) {
                    System.err.println("ScriptEngineManager providers.next(): "
                                 + err.getMessage());
                    if (DEBUG) {
                        err.printStackTrace();
                    }
                    // one factory failed, but check other factories...
                    continue;
                }
            }
        } catch (ServiceConfigurationError err) {
            System.err.println("ScriptEngineManager providers.hasNext(): " 
                            + err.getMessage());
            if (DEBUG) {
                err.printStackTrace();
            }
            // do not throw any exception here. user may want to
            // manage his/her own factories using this manager
            // by explicit registratation (by registerXXX) methods.
            return;
        }
    }
    
    /**
     * <code>setBindings</code> stores the specified <code>Bindings</code>
     * in the <code>globalScope</code> field. ScriptEngineManager sets this
     * <code>Bindings</code> as global bindings for <code>ScriptEngine</code>
     * objects created by it.
     *
     * @param bindings The specified <code>Bindings</code>
     * @throws IllegalArgumentException if bindings is null.
     */
    public void setBindings(Bindings bindings) {
        if (bindings == null) {
            throw new IllegalArgumentException("Global scope cannot be null.");
        }
        
        globalScope = bindings;
    }
    
    /**
     * <code>getBindings</code> returns the value of the <code>globalScope</code> field.
     * ScriptEngineManager sets this <code>Bindings</code> as global bindings for 
     * <code>ScriptEngine</code> objects created by it.
     *
     * @return The globalScope field.
     */
    public Bindings getBindings() {
        return globalScope;
    }
    
    /**
     * Sets the specified key/value pair in the Global Scope.
     * @param key Key to set
     * @param value Value to set.
     * @throws NullPointerException if key is null.
     * @throws IllegalArgumentException if key is empty string.
     */
    public void put(String key, Object value) {
        globalScope.put(key, value);
    }
    
    /**
     * Gets the value for the specified key in the Global Scope
     * @param key The key whose value is to be returned.
     * @return The value for the specified key.
     */
    public Object get(String key) {
        return globalScope.get(key);
    }
    
    /**
     * Looks up and creates a <code>ScriptEngine</code> for a given  name.
     * The algorithm first searches for a <code>ScriptEngineFactory</code> that has been
     * registered as a handler for the specified name using the <code>registerEngineName</code>
     * method.
     * <br><br> If one is not found, it searches the array of <code>ScriptEngineFactory</code> instances
     * stored by the constructor for one with the specified name.  If a <code>ScriptEngineFactory</code>
     * is found by either method, it is used to create instance of <code>ScriptEngine</code>.
     * @param shortName The short name of the <code>ScriptEngine</code> implementation.
     * returned by the <code>getNames</code> method of its <code>ScriptEngineFactory</code>.
     * @return A <code>ScriptEngine</code> created by the factory located in the search.  Returns null
     * if no such factory was found.  The <code>ScriptEngineManager</code> sets its own <code>globalScope</code>
     * <code>Bindings</code> as the <code>GLOBAL_SCOPE</code> <code>Bindings</code> of the newly
     * created <code>ScriptEngine</code>.
     * @throws NullPointerException if shortName is null.
     */
    public ScriptEngine getEngineByName(String shortName) {
        if (shortName == null) throw new NullPointerException();
        //look for registered name first
        Object obj;
        if (null != (obj = nameAssociations.get(shortName))) {
            ScriptEngineFactory spi = (ScriptEngineFactory)obj;
            try {
                ScriptEngine engine = spi.getScriptEngine();
                engine.setBindings(getBindings(), ScriptContext.GLOBAL_SCOPE);
                return engine;
            } catch (Exception exp) {
                if (DEBUG) exp.printStackTrace();
            }
        }
        
        for (ScriptEngineFactory spi : engineSpis) {
            List<String> names = null;
            try {
                names = spi.getNames();
            } catch (Exception exp) {
                if (DEBUG) exp.printStackTrace();
            }
            
            if (names != null) {
                for (String name : names) {
                    if (shortName.equals(name)) {
                        try {
                            ScriptEngine engine = spi.getScriptEngine();
                            engine.setBindings(getBindings(), ScriptContext.GLOBAL_SCOPE);
                            return engine;
                        } catch (Exception exp) {
                            if (DEBUG) exp.printStackTrace();
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Look up and create a <code>ScriptEngine</code> for a given extension.  The algorithm
     * used by <code>getEngineByName</code> is used except that the search starts
     * by looking for a <code>ScriptEngineFactory</code> registered to handle the
     * given extension using <code>registerEngineExtension</code>.
     * @param extension The given extension
     * @return The engine to handle scripts with this extension.  Returns <code>null</code>
     * if not found.
     * @throws NullPointerException if extension is null.
     */
    public ScriptEngine getEngineByExtension(String extension) {
        if (extension == null) throw new NullPointerException();
        //look for registered extension first
        Object obj;
        if (null != (obj = extensionAssociations.get(extension))) {
            ScriptEngineFactory spi = (ScriptEngineFactory)obj;
            try {
                ScriptEngine engine = spi.getScriptEngine();
                engine.setBindings(getBindings(), ScriptContext.GLOBAL_SCOPE);
                return engine;
            } catch (Exception exp) {
                if (DEBUG) exp.printStackTrace();
            }
        }
        
        for (ScriptEngineFactory spi : engineSpis) {
            List<String> exts = null;
            try {
                exts = spi.getExtensions();
            } catch (Exception exp) {
                if (DEBUG) exp.printStackTrace();
            }
            if (exts == null) continue;
            for (String ext : exts) {
                if (extension.equals(ext)) {
                    try {
                        ScriptEngine engine = spi.getScriptEngine();
                        engine.setBindings(getBindings(), ScriptContext.GLOBAL_SCOPE);
                        return engine;
                    } catch (Exception exp) {
                        if (DEBUG) exp.printStackTrace();
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Look up and create a <code>ScriptEngine</code> for a given mime type.  The algorithm
     * used by <code>getEngineByName</code> is used except that the search starts
     * by looking for a <code>ScriptEngineFactory</code> registered to handle the
     * given mime type using <code>registerEngineMimeType</code>.
     * @param mimeType The given mime type
     * @return The engine to handle scripts with this mime type.  Returns <code>null</code>
     * if not found.
     * @throws NullPointerException if mimeType is null.
     */
    public ScriptEngine getEngineByMimeType(String mimeType) {
        if (mimeType == null) throw new NullPointerException();
        //look for registered types first
        Object obj;
        if (null != (obj = mimeTypeAssociations.get(mimeType))) {
            ScriptEngineFactory spi = (ScriptEngineFactory)obj;
            try {
                ScriptEngine engine = spi.getScriptEngine();
                engine.setBindings(getBindings(), ScriptContext.GLOBAL_SCOPE);
                return engine;
            } catch (Exception exp) {
                if (DEBUG) exp.printStackTrace();
            }
        }
        
        for (ScriptEngineFactory spi : engineSpis) {
            List<String> types = null;
            try {
                types = spi.getMimeTypes();
            } catch (Exception exp) {
                if (DEBUG) exp.printStackTrace();
            }
            if (types == null) continue;
            for (String type : types) {
                if (mimeType.equals(type)) {
                    try {
                        ScriptEngine engine = spi.getScriptEngine();
                        engine.setBindings(getBindings(), ScriptContext.GLOBAL_SCOPE);
                        return engine;
                    } catch (Exception exp) {
                        if (DEBUG) exp.printStackTrace();
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Returns an array whose elements are instances of all the <code>ScriptEngineFactory</code> classes
     * found by the discovery mechanism.
     * @return List of all discovered <code>ScriptEngineFactory</code>s.
     */
    public List<ScriptEngineFactory> getEngineFactories() {
        List<ScriptEngineFactory> res = new ArrayList<ScriptEngineFactory>(engineSpis.size());
        for (ScriptEngineFactory spi : engineSpis) {
            res.add(spi);
        }
        return Collections.unmodifiableList(res);
    }
    
    /**
     * Registers a <code>ScriptEngineFactory</code> to handle a language
     * name.  Overrides any such association found using the Discovery mechanism.
     * @param name The name to be associated with the <code>ScriptEngineFactory</code>.
     * @param factory The class to associate with the given name.
     * @throws NullPointerException if any of the parameters is null.
     */
    public void registerEngineName(String name, ScriptEngineFactory factory) {
        if (name == null || factory == null) throw new NullPointerException();
        nameAssociations.put(name, factory);
    }
    
    /**
     * Registers a <code>ScriptEngineFactory</code> to handle a mime type.
     * Overrides any such association found using the Discovery mechanism.
     *
     * @param type The mime type  to be associated with the
     * <code>ScriptEngineFactory</code>.
     *
     * @param factory The class to associate with the given mime type.
     * @throws NullPointerException if any of the parameters is null.
     */
    public void registerEngineMimeType(String type, ScriptEngineFactory factory) {
        if (type == null || factory == null) throw new NullPointerException();
        mimeTypeAssociations.put(type, factory);
    }
    
    /**
     * Registers a <code>ScriptEngineFactory</code> to handle an extension.
     * Overrides any such association found using the Discovery mechanism.
     *
     * @param extension The extension type  to be associated with the
     * <code>ScriptEngineFactory</code>.
     * @param factory The class to associate with the given extension.
     * @throws NullPointerException if any of the parameters is null.
     */
    public void registerEngineExtension(String extension, ScriptEngineFactory factory) {
        if (extension == null || factory == null) throw new NullPointerException();
        extensionAssociations.put(extension, factory);
    }
    
    /** Set of script engine factories discovered. */
    private HashSet<ScriptEngineFactory> engineSpis;
    
    /** Map of engine name to script engine factory. */
    private HashMap<String, ScriptEngineFactory> nameAssociations;
    
    /** Map of script file extension to script engine factory. */
    private HashMap<String, ScriptEngineFactory> extensionAssociations;
    
    /** Map of script script MIME type to script engine factory. */
    private HashMap<String, ScriptEngineFactory> mimeTypeAssociations;
    
    /** Global bindings associated with script engines created by this manager. */
    private Bindings globalScope;
    
    private boolean canCallerAccessLoader(ClassLoader loader) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader callerLoader = getCallerClassLoader();
            if (callerLoader != null) {
                if (loader != callerLoader || !isAncestor(loader, callerLoader)) {
                    try {
                        sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
                    } catch (SecurityException exp) {
                        if (DEBUG) exp.printStackTrace();
                        return false;
                    }
                } // else fallthru..
            } // else fallthru..
        } // else fallthru..
        
        return true;
    }
    
    // Note that this code is same as ClassLoader.getCallerClassLoader().
    // But, that method is package private and hence we can't call here.
    private ClassLoader getCallerClassLoader() {
        Class caller = Reflection.getCallerClass(3);
        if (caller == null) {
            return null;
        }
        return caller.getClassLoader();
    }
    
    // is cl1 ancestor of cl2?
    private boolean isAncestor(ClassLoader cl1, ClassLoader cl2) {
        do {
            cl2 = cl2.getParent();
            if (cl1 == cl2) return true;
        } while (cl2 != null);
        return false;
    }
}
