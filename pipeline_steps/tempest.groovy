def tempest_install(vm=null){
  // NOTE(mkam): Can remove ANSIBLE_CACHE_PLUGIN when we no longer gate stable/mitaka
  common.openstack_ansible(
    vm: vm,
    playbook: "run_tempest.yml",
    args: "-t tempest_install",
    path: "/opt/rpc-openstack/scripts",
    environment_vars: ["ANSIBLE_CACHE_PLUGIN=memory"]
  )
}

def tempest_run(wrapper="") {
  def output = sh (script: """#!/bin/bash
  ${wrapper} cd /opt/rpc-openstack/scripts && openstack-ansible \
    run_tempest.yml -t tempest_execute_tests
  """, returnStdout: true)
  print output
  return output
}


/* if tempest install fails, don't bother trying to run or collect test results
 * however if running fails, we should still collect the failed results
 */
def tempest(infra_vm=null, deploy_vm=null){
  if (infra_vm != null) {
    wrapper = "sudo ssh -T -oStrictHostKeyChecking=no ${infra_vm}"
    copy_cmd = "scp -o StrictHostKeyChecking=no -p  -r infra1:"
  } else{
    wrapper = ""
    copy_cmd = "cp -p "
  }
  common.conditionalStage(
    stage_name: "Install Tempest",
    stage: {
      tempest_install(deploy_vm)
    }
  )
  common.conditionalStage(
    stage_name: "Tempest Tests",
    stage: {
      try{
        def result = tempest_run(wrapper)
        def second_result = ""
        if(result.contains("Race in testr accounting.")){
          second_result = tempest_run(wrapper)
        }
        if(second_result.contains("Race in testr accounting.")) {
          currentBuild.result = 'FAILURE'
        }
        } catch (e){
        print(e)
        throw(e)
      } finally{
        sh """
        rm -f *tempest*.xml
        ${copy_cmd}/openstack/log/*utility*/**/*tempest*.xml . ||:
        ${copy_cmd}/openstack/log/*utility*/*tempest*.xml . ||:
        """
        junit allowEmptyResults: true, testResults: '*tempest*.xml'
      } //finally
    } //stage
  ) //conditionalStage
} //func


return this;
