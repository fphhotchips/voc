package org.python.types;

public class Type extends org.python.types.Object implements org.python.Callable {
    public enum Origin {PLACEHOLDER, BUILTIN, PYTHON, JAVA};
    public java.lang.String PYTHON_TYPE_NAME;
    private static java.util.Map<java.lang.Class, org.python.types.Type> known_types = new java.util.HashMap<java.lang.Class, org.python.types.Type>();

    java.lang.reflect.Constructor constructor;

    /**
     * Factory method to obtain Python classes from their Java counterparts
     */
    public static org.python.types.Type pythonType(java.lang.Class java_class) {
        // Look up the class in the known types table.
        org.python.types.Type python_type = known_types.get(java_class);
        if (python_type == null) {
            // If type isn't known, create and store a placeholder
            // so that recursive lookups have a termination condition.
            PlaceholderType placeholder = new PlaceholderType(java_class);
            known_types.put(java_class, placeholder);

            // Construct the new type, and store it in the types table.
            // Any type implementing org.python.Object is a Python type;
            // otherwise, wrap it as a native Java type.
            if (org.python.Object.class.isAssignableFrom(java_class)) {
                if (java_class.getName().startsWith("org.python.types.")) {
                    python_type = new org.python.types.Type(java_class, Origin.BUILTIN);
                } else {
                    python_type = new org.python.types.Type(java_class, Origin.PYTHON);
                }
            } else {
                python_type = new org.python.java.Type(java_class);
            }
            known_types.put(java_class, python_type);

            // Since we have a freshly created type, resolve
            // any placeholders that referenced this type.
            // These will have been created as a consequence of
            // calling the constructor for this type.
            placeholder.resolve(python_type);
        }
        return python_type;
    }

    public static org.python.types.Type pythonType(java.lang.String java_class_name) {
        try {
            return pythonType(java.lang.Class.forName(java_class_name.replace("/", ".")));
        } catch (ClassNotFoundException e) {
            throw new org.python.exceptions.RuntimeError("Unknown Class");
        }
    }

    /**
     * Convert a Java instance into the a Python-wrapped type.
     *
     * This means converting:
     *    * `null` into a None
     *    * Returning any object that already implements org.python.Object as itself.
     *    * Wrapping any other object in a org.python.java.Object wrapper.
     */
    public static org.python.Object toPython(java.lang.Object value) {
        if (value == null) {
            return org.python.types.NoneType.NONE;
        } else {
            if (org.python.Object.class.isAssignableFrom(value.getClass())) {
                return (org.python.Object) value;
            } else {
                return new org.python.java.Object(value);
            }
        }
    }

    public java.lang.Class klass;
    public Origin origin;

    /**
     * A utility method to update the internal value of this object.
     *
     * Used by __i*__ operations to do an in-place operation.
     * obj must be of type org.python.types.Type
     */
    void setValue(org.python.Object obj) {
        this.klass = ((org.python.types.Type) obj).klass;
    }

    public Type(java.lang.Class klass, Origin origin) {
        super(origin, null);
        if (origin != Origin.PLACEHOLDER) {
            this.klass = klass;

            this.attrs.put("__name__", new org.python.types.Str(this.klass.getName()));
            this.attrs.put("__qualname__", new org.python.types.Str(this.klass.getName()));
            // this.attrs.put("__module__", );
        }

        if (origin == Origin.BUILTIN) {
            org.Python.initializeModule(klass, this.attrs);
        } else if (origin == Origin.PYTHON) {
            try {
                this.constructor = this.klass.getConstructor(org.python.Object[].class, java.util.Map.class);
            } catch (java.lang.NoSuchMethodException e) {
                this.constructor = null;
            }
        }
    }

    public Type(java.lang.Class klass) {
        this(klass, Origin.PYTHON);
    }

    public void add_reference(org.python.Object instance) {
        throw new java.lang.RuntimeException("Can't add reference to normal type");
    }

    @org.python.Method(
        __doc__ = ""
    )
    public org.python.types.Str __repr__() {
        return new org.python.types.Str(String.format("<class '%s'>", org.Python.typeName(this.klass)));
    }

    public org.python.Object __getattribute_null(java.lang.String name) {
        // System.out.println("GETATTRIBUTE CLASS " + this + " " + name);
        // System.out.println("CLASS ATTRS " + this.attrs);
        org.python.Object value = this.attrs.get(name);

        if (value == null) {
            // The class attributes didn't contain the object. We must
            // differentiate between "doesn't exist" and "value is null";
            // If the key *doesn't* exist in the attributes dictionary,
            // try to look it up. If it doesn't exist as a field, then
            // store a null to represent this fact, so we won't look again.
            if (!this.attrs.containsKey(name)) {
                try {
                    value = new org.python.java.Field(klass.getField(name));
                } catch (java.lang.NoSuchFieldException e) {
                    value = null;
                }
                // If the field doesn't exist, store a value of null
                // so that we don't try to look up the field again.
                this.attrs.put(name, value);
            }
        }
        return value;
    }

    public void __setattr__(java.lang.String name, org.python.Object value) {
        if (!this.__setattr_null(name, value)) {
            throw new org.python.exceptions.TypeError("can't set attributes of built-in/extension type '" + org.Python.typeName(this.klass) + "'");
        }
    }

    public boolean __setattr_null(java.lang.String name, org.python.Object value) {
        // System.out.println("SETATTRIBUTE TYPE " + this + " " + name + " = " + value);
        // System.out.println("class attrs = " + this.attrs);

        // Can't set attributes of builtin types.
        if (this.origin == Origin.BUILTIN) {
            return false;
        }

        this.attrs.put(name, value);
        return true;
    }

    public org.python.Object invoke(org.python.Object [] args, java.util.Map<java.lang.String, org.python.Object> kwargs) {
        try {
            // System.out.println("CONSTRUCTOR :" + this.constructor);
            // System.out.println("TYPE: " + this);
            // System.out.println("ARGS:");
            // for (org.python.Object arg: args) {
            //     System.out.println("  " + arg);
            // }

            // System.out.println("KWARGS:");
            // for (java.lang.String argname: kwargs.keySet()) {
            //     System.out.println("  " + argname + " = " + kwargs.get(argname));
            // }
            if (this.constructor != null) {
                return (org.python.Object) this.constructor.newInstance(args, kwargs);
            } else {
                throw new org.python.exceptions.RuntimeError("No Python-compatible constructor for type " + this.klass);
            }
        } catch (java.lang.IllegalAccessException e) {
            throw new org.python.exceptions.RuntimeError("Illegal access to Java constructor " + this.constructor);
        } catch (java.lang.reflect.InvocationTargetException e) {
            try {
                // e.getTargetException().printStackTrace();
                // If the Java method raised an Python exception, re-raise that
                // exception as-is. If it wasn't a Python exception, wrap it
                // as one and continue.
                throw (org.python.exceptions.BaseException) e.getCause();
            } catch (ClassCastException java_e) {
                throw new org.python.exceptions.RuntimeError(e.getCause().toString());
            }
        } catch (java.lang.InstantiationException e) {
            throw new org.python.exceptions.RuntimeError(e.getCause().toString());
        } finally {
        //     System.out.println("CONSTRUCTOR DONE");
        }
    }
}
