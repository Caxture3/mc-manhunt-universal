package com.caxture.manhuntuniversal.api.text;
/** Fast main-thread template provider. Null falls through to the normal language resolution. */
@FunctionalInterface public interface TextProvider { String provide(TextContext context); }
