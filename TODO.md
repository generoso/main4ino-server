# TODO

  - Document the return type of each method of the REST API
  - Make the naming for deviceName/dev/device homogeneous: dev
  - Make the naming for actor/act/a homogeneous: act

# DONE

  - Speed up 'last' request from device as it takes way too long (>10secs !!!)
  - Perform cleanup of repository, service v1 and its doc, and url class
  - Make the DevLogger tests write to a temporary directory that is afterwards cleaned up
  - Make a check on status transitions: forbid invalid transitions and document them
  - Migrate HELP as documentation in Service
  - Do a check to ensure that documentation matches service v1
  - Resolve code TODOs
  - Make tuples/actortuples/propsmap/map/etc.etc.etc. simpler
  - Render target/report and properties statuses type-safe
  - Consume done at transaction/request level, one transaction at a time, no merging of different transactions
  - Remove unneeded API methods
  - Be able to change the target/report status
  - Go at Device level in Translator, avoid going to ActorTup level
  - Add target/report status and tuples creation timestamp
  - Allow to create a target/report for a given device and fill it in actor per actor
  - Auto-format code
  - Document which parts of the API have which usecase.
  - Have logs (in a purely functional way if possible)
  - Do not display None keys in the JSON -> by displaying more appropriate types according to the queries
  - Handle non happy paths too
  - Handle properly scenarios where no properties are found in a GET
  - Add pagination/ranges to streamed resources
  - webapp
  - Many times the in sql the ID is obtained to later obtain metadata and then the props, it would be good to get the metadata together with the ids.
  - There is too much duplicate code in the Repository due to the fact I don't see how reuse SQL queries patterns with an interpolator
  - Authentication per device
  - Be able to choose between history of targets or reports
  - Allow pre-filled examples in summary
