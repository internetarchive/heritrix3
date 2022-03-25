#!/bin/bash

_JOBARGS="-b /"

# set credentials (require both USERNAME and PASSWORD)
# -a "${USERNAME}:${PASSWORD}"
if [[ ! -z "$USERNAME" ]] && [[ ! -z "$PASSWORD" ]]; then
    echo "${USERNAME}:${PASSWORD}" > ${HERITRIX_HOME}/credentials.txt
    _JOBARGS="$_JOBARGS -a @${HERITRIX_HOME}/credentials.txt"
elif [[ ! -z "$CREDSFILE" ]]; then
    _JOBARGS="$_JOBARGS -a @${CREDSFILE}"
else
    >&2 echo "No USERNAME and/or PASSWORD environment var set!"
fi

# check if -r mode (only from version 3.4.0-20210803)
if [[ ! -z "$JOBNAME" ]]; then
    >&2 echo "Found JOBNAME envvar, just running job: $JOBNAME"
    _JOBARGS="$_JOBARGS -r $JOBNAME"
    if [ ! -f "/opt/heritrix/jobs/$JOBNAME/crawler-beans.cxml" ]; then
        >&2 echo "Did not find any 'crawler-beans.cxml' for job '$JOBNAME'!"
    fi
fi

# run
exec ${HERITRIX_HOME}/bin/heritrix $_JOBARGS
